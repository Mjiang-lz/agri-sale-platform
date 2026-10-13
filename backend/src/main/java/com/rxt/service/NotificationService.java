package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.NotificationType;
import com.rxt.vo.NotificationVO;

/**
 * 站内通知业务接口。
 *
 * <p><b>这是一个跨模块的公共能力</b>，不是某个业务模块的私有服务：
 * 订单（M2）、专家问答与预约（M4/M5）、融资（M0）都通过 {@link #send} 往里写，
 * 通知中心（本服务读侧）统一往外读。</p>
 *
 * <p>读侧的四个方法都<b>只接受 userId 作为数据范围</b>，不接受「查谁的通知」这类入参 ——
 * 身份一律由 Controller 从 {@code UserContext} 取当前登录用户后传进来，
 * 客户端无法通过参数越权查看他人通知。</p>
 */
public interface NotificationService {

    /**
     * 写入一条站内通知。
     *
     * <p><b>本方法不抛异常、不阻断调用方的业务事务。</b>
     * 「订单已发货」这件事不能因为通知写不进去而失败 —— 丢一条通知比丢一笔业务动作轻得多。
     * 因此内部对非法参数（接收人为空、类型不在白名单、标题正文为空）直接跳过，
     * 超长内容截断到列宽，数据库异常记录日志后吞掉。
     * 调用方**无需** try-catch，也不该把本调用包进「失败就回滚订单」的逻辑里。</p>
     *
     * @param userId  接收人（t_user.user_id）。为 null 时跳过
     * @param type    事件类型，取值见 {@link NotificationType}。非法时跳过
     * @param title   标题，超 50 字符会被截断
     * @param content 正文摘要，超 200 字符会被截断
     * @param bizId   关联业务主键，可为 null
     */
    void send(Long userId, NotificationType type, String title, String content, Long bizId);

    /**
     * 我的通知列表，按产生时间倒序（同一时间再按主键倒序，保证翻页顺序稳定）。
     *
     * @param userId   当前登录用户
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数
     * @param isRead   0 只看未读、1 只看已读、null 全部；其它值抛 400
     */
    IPage<NotificationVO> pageMine(Long userId, long pageNum, long pageSize, Integer isRead);

    /**
     * 我的未读数。顶栏红点用，走 {@code (user_id, is_read, create_time)} 索引前缀。
     */
    long countUnread(Long userId);

    /**
     * 标记单条已读。
     *
     * <p><b>幂等</b>：已经是已读的再标一次仍返回成功，且<b>不刷新 read_time</b> ——
     * 保留「第一次看到的时间」，否则用户手快点两下就把证据改成了第二次的时间。</p>
     *
     * @param userId         当前登录用户
     * @param notificationId 通知主键
     * @throws com.rxt.common.BizException 通知不存在、或不属于当前用户（404。两种情况同码，
     *                                     否则拿一串 id 试一遍就能探测出哪些通知存在、属于谁）
     */
    void markRead(Long userId, Long notificationId);

    /**
     * 把本人所有未读标记为已读。没有未读时同样成功（幂等）。
     *
     * @param userId 当前登录用户
     */
    void markAllRead(Long userId);
}
