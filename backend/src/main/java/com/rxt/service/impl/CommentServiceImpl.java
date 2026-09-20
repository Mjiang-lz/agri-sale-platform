package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.CommentPublishDTO;
import com.rxt.entity.Comment;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.entity.User;
import com.rxt.mapper.CommentMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.service.CommentService;
import com.rxt.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品评论业务实现。
 *
 * <p>两条硬规则（来自子任务清单的验收标准）：
 * <ol>
 *   <li><b>仅已购用户可评论</b> —— 判定口径为「存在该买家对该商品的付款后订单」，
 *       即订单状态属于 {@link #PURCHASED_ORDER_STATUSES}。待支付（没付钱）、
 *       已取消（钱退了）都不算买到，一律拒绝。</li>
 *   <li><b>新评论默认待审核</b> —— 落库即 {@code 待审核}，前台查询接口只回
 *       {@code 已通过}，所以「新评论不出现，直到审核通过」是天然成立的，不需要额外开关。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 评论对象类型：商品 */
    private static final String TYPE_PRODUCT = "PRODUCT";

    /** 业务表审核词表：待审核 / 已通过 / 未通过 */
    private static final String AUDIT_PENDING = "待审核";
    private static final String AUDIT_APPROVED = "已通过";

    /**
     * 「已购」口径：付过款之后的订单状态。
     *
     * <p>t_order 没有独立的支付标记，但「待发货」本身就是支付成功后的状态
     * （待支付 → 待发货），所以这三个状态等同于「钱已经付了」。
     * 要收紧成「确认收货后才能评价」，把这里改成只留 {@code 已完成} 即可，
     * 业务口径集中在这一个常量上，改一处就够。</p>
     */
    private static final List<String> PURCHASED_ORDER_STATUSES = List.of("待发货", "待收货", "已完成");

    private final CommentMapper commentMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    @Override
    public CommentVO publishProductComment(Long userId, CommentPublishDTO dto) {
        // 1. 正文规范化后再量长度。@Size 校验的是原文，像 " 好 " 这种前后带空格、
        //    实际只有 1 个字的输入能钻过校验，所以在业务层用 trim 后的长度再兜一道。
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.length() < 2) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "评论内容至少 2 个字");
        }

        // 2. 商品必须存在。注意这里**不校验上下架/审核状态**：
        //    买家当初是买到手了才来评价的，商品后来被下架不该剥夺他的评价权。
        if (productMapper.selectById(dto.getProductId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }

        // 3. 已购校验（验收点一：未购买用户评论被拒）
        if (!hasPurchased(userId, dto.getProductId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅购买过该商品的用户可发表评论");
        }

        // 4. 防重复：同一用户对同一商品，已有「待审核」或「已通过」的评论则拒绝；
        //    被驳回（未通过）的可以重新提交 —— 不然审核不过就永远没机会改了。
        Long duplicated = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, userId)
                .eq(Comment::getTargetType, TYPE_PRODUCT)
                .eq(Comment::getTargetId, dto.getProductId())
                .in(Comment::getAuditStatus, AUDIT_PENDING, AUDIT_APPROVED));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "你已评价过该商品，请等待审核结果");
        }

        // 5. 落库。createTime 由 MybatisMetaObjectHandler 自动填充，无需手工赋值。
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(TYPE_PRODUCT);
        comment.setTargetId(dto.getProductId());
        comment.setContent(content);
        comment.setAuditStatus(AUDIT_PENDING);
        commentMapper.insert(comment);

        // 6. 回读成 VO 返回，接口自解释「这条评论现在是什么状态」，前端据此提示「审核中」
        CommentVO vo = convertToVO(comment);
        fillUserInfo(List.of(vo));
        return vo;
    }

    @Override
    public IPage<CommentVO> pageProductComments(Long productId, long pageNum, long pageSize) {
        if (productId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "缺少商品参数");
        }

        // 三个条件正好命中 idx_comment_target (target_type, target_id, audit_status)，
        // 不必额外加索引；排序字段 create_time 在索引外，但单商品评论量级小，可接受。
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, TYPE_PRODUCT)
                .eq(Comment::getTargetId, productId)
                .eq(Comment::getAuditStatus, AUDIT_APPROVED)
                .orderByDesc(Comment::getCreateTime);

        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        IPage<CommentVO> result = page.convert(this::convertToVO);

        // 这一页的评论人一次性补齐，避免在循环里逐条查用户（N+1）
        fillUserInfo(result.getRecords());
        return result;
    }

    /**
     * 判断用户是否购买过该商品（「已购」口径见 {@link #PURCHASED_ORDER_STATUSES}）。
     */
    private boolean hasPurchased(Long userId, Long productId) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getBuyerId, userId)
                .eq(Order::getProductId, productId)
                .in(Order::getOrderStatus, PURCHASED_ORDER_STATUSES));
        return count != null && count > 0;
    }

    /**
     * 评论实体转视图对象。
     */
    private CommentVO convertToVO(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setCommentId(comment.getCommentId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setAuditStatus(comment.getAuditStatus());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    /**
     * 批量补齐评论人姓名与头像。
     *
     * <p>做法：先收集本页所有 userId 去重，一次 IN 查询拿回来做成 Map 再回填。
     * 千万不要写成 {@code for (vo : vos) { vo.setUserName(userMapper.selectById(...)); }}
     * —— 一页 10 条就是 10 次数据库往返。</p>
     */
    private void fillUserInfo(List<CommentVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }

        Set<Long> userIds = vos.stream()
                .map(CommentVO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        // 只 select 需要的三列：phone / password 这类敏感字段不查、不带出
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getUserId, User::getRealName, User::getAvatarUrl)
                .in(User::getUserId, userIds));
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));

        for (CommentVO vo : vos) {
            User user = userMap.get(vo.getUserId());
            // 用户可能已被删除，允许为 null，前端按「匿名用户」兜底展示
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setAvatarUrl(user.getAvatarUrl());
            }
        }
    }
}
