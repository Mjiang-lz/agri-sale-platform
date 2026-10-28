package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.NotificationType;
import com.rxt.common.ResultCode;
import com.rxt.entity.Notification;
import com.rxt.mapper.NotificationMapper;
import com.rxt.service.NotificationService;
import com.rxt.vo.NotificationVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内通知业务实现。
 *
 * <h3>写侧（send）的三条处置原则</h3>
 * <ol>
 *   <li><b>绝不打断调用方的业务。</b>「订单已发货」不能因为通知写不进去而失败。
 *       非法入参跳过、超长截断、库异常记录后吞掉 —— 本方法对外不抛业务异常。
 *       代价是极端情况下会丢一条通知，比丢一笔业务动作轻得多。</li>
 *   <li><b>与被调用方共用事务。</b>不另开 {@code REQUIRES_NEW}：那样会出现
 *       「通知说已发货、订单却回滚了」的错位 —— 一条错误的通知比没有通知更糟。
 *       共用事务意味着两者同生共灭，一致性由事务保证。</li>
 *   <li><b>落库前就把长度收口。</b>title 截到 50、content 截到 200，与 DDL 列宽逐字段对齐。
 *       不依赖 MySQL 的行为 —— 宽松模式下静默截断、严格模式下报错，
 *       两种模式给出完全不同的结果，那是最难排查的一类问题。</li>
 * </ol>
 *
 * <h3>读侧的数据范围</h3>
 * <p>四个读方法一律以传入的 {@code userId} 作为唯一数据范围，
 * 且 {@code userId} 只由 Controller 从登录态取。没有「查谁的通知」这类入参，
 * 客户端无法通过参数越权。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 未读 */
    private static final int UNREAD = 0;

    /** 已读 */
    private static final int READ = 1;

    /** title 列宽（VARCHAR(50)），按字符数而非字节数计 */
    private static final int TITLE_MAX = 50;

    /** content 列宽（VARCHAR(200)） */
    private static final int CONTENT_MAX = 200;

    private final NotificationMapper notificationMapper;

    /* ==================================================================
     * 写侧
     * ================================================================== */

    @Override
    public void send(Long userId, NotificationType type, String title, String content, Long bizId) {
        if (userId == null || type == null) {
            // 接收人与类型是唯二的必填项。缺任一项意味着调用方写错了，
            // 属于开发期问题 —— 记 warn 让它暴露出来，但不抛异常连累业务。
            log.warn("站内通知入参不完整，已跳过。userId={} type={} title={}", userId, type, title);
            return;
        }
        String safeTitle = truncate(title, TITLE_MAX);
        String safeContent = truncate(content, CONTENT_MAX);
        if (safeTitle.isEmpty()) {
            log.warn("站内通知标题为空，已跳过。userId={} type={}", userId, type);
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.name());
        notification.setTitle(safeTitle);
        notification.setContent(safeContent);
        notification.setBizId(bizId);
        notification.setIsRead(UNREAD);
        // readTime 显式留 null：未读的定义就是「还没有已读时间」

        try {
            notificationMapper.insert(notification);
            log.debug("站内通知已写入 userId={} type={} bizId={} title={}",
                    userId, type, bizId, safeTitle);
        } catch (Exception e) {
            // 吞掉而不是上抛：见类注释第 1 条。注意 catch 的是 Exception 而不是
            // DataAccessException —— 这里的目的是「绝不打断业务」，漏掉任何一种异常
            // 都会让「通知写不进去」升级成「订单发货失败」。
            log.error("站内通知写入失败（已忽略，不影响业务）userId={} type={} bizId={}",
                    userId, type, bizId, e);
        }
    }

    /* ==================================================================
     * 读侧
     * ================================================================== */

    @Override
    public IPage<NotificationVO> pageMine(Long userId, long pageNum, long pageSize, Integer isRead) {
        requireValidIsRead(isRead);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        // 只按 create_time 排的话，同一秒写入的两条（例如一单同时通知买卖双方）
        // 在不同页里顺序可能互换，翻页时出现「这条刚看过又冒出来」。
        // 加主键兜底让顺序稳定 —— 这是分页正确性的必要条件，不是锦上添花。
        wrapper.orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getNotificationId);

        Page<Notification> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<NotificationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<NotificationVO> records = page.getRecords().stream().map(this::toVO).toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public long countUnread(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, UNREAD);
        return notificationMapper.selectCount(wrapper);
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        if (notificationId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请指定通知");
        }
        Notification exist = notificationMapper.selectById(notificationId);
        // 越权与不存在返回同一种错误：否则拿一串 id 试一遍就能探测出
        // 「哪些通知存在、属于谁」，那是可被利用的信息泄露。
        // 与 AddressServiceImpl.requireOwnAddress 同一口径。
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "通知不存在");
        }
        if (Integer.valueOf(READ).equals(exist.getIsRead())) {
            // 已经读过：直接返回成功，且**不刷新 read_time**。
            // 幂等在这里是语义要求而不是偷懒 —— read_time 记的是「用户第一次看到的时间」，
            // 手快点两下就把它改掉的话，这份记录就不再能回答「用户到底什么时候看到的」。
            return;
        }

        // 条件更新（带上前置状态）而不是 updateById：
        // 并发下两个请求同时把同一条从未读改成已读时，只有一个会命中 is_read=0 的条件。
        // 与订单状态流转同一写法。
        LambdaUpdateWrapper<Notification> update = new LambdaUpdateWrapper<>();
        update.eq(Notification::getNotificationId, notificationId)
                .eq(Notification::getIsRead, UNREAD)
                .set(Notification::getIsRead, READ)
                .setSql("read_time = NOW()");
        int rows = notificationMapper.update(null, update);
        log.debug("标记通知已读 userId={} notificationId={} rows={}", userId, notificationId, rows);
    }

    @Override
    public void markAllRead(Long userId) {
        LambdaUpdateWrapper<Notification> update = new LambdaUpdateWrapper<>();
        update.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, UNREAD)
                .set(Notification::getIsRead, READ)
                .setSql("read_time = NOW()");
        // 条件里已经带 is_read=0，所以「没有未读」时 rows=0 ——
        // 那不是失败，本方法依然返回成功（幂等）。不需要额外判断 rows。
        int rows = notificationMapper.update(null, update);
        log.debug("全部标记已读 userId={} rows={}", userId, rows);
    }

    /* ==================================================================
     * 内部方法
     * ================================================================== */

    /**
     * isRead 只接受 0 / 1 / 不传。
     *
     * <p>不把非法值悄悄当成「不筛选」：那会让前端传错参数时看到一个
     * 「看起来对」的全量列表，问题被推迟到用户报障才暴露。</p>
     */
    private void requireValidIsRead(Integer isRead) {
        if (isRead != null && isRead != UNREAD && isRead != READ) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "isRead 只支持 0（未读）或 1（已读）");
        }
    }

    /**
     * 按字符数截断，null 归一成空串。
     *
     * <p>用 {@code length()}（UTF-16 码元数）而不是码点数：对 BMP 内的汉字两者相等，
     * 而遇到增补平面字符（emoji）时 {@code length()} 更大，截断只会更保守，
     * 不会算出比列宽还长的结果。宁可少存一个字符，也不撞列宽。</p>
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setNotificationId(notification.getNotificationId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBizId(notification.getBizId());
        vo.setIsRead(notification.getIsRead());
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());
        // 类型认不出时 link 为 null（不抛错）：一条老数据不该让整页打不开
        vo.setLink(NotificationType.linkOf(notification.getType()));
        return vo;
    }
}
