package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.CommentPublishDTO;
import com.rxt.vo.CommentVO;

import java.util.Collection;
import java.util.Map;

/**
 * 商品评论业务接口。
 *
 * <p>评论数据存放在全站共用的 {@code t_comment} 表（通过 {@code target_type}
 * 区分商品评论与知识评论）。本接口当前只覆盖商品评论，知识评论不在本次范围内。</p>
 */
public interface CommentService {

    /**
     * 发表商品评论。
     *
     * <p>规则：仅「已购」用户可评论；新评论一律落为「待审核」，前台不可见，
     * 直到管理员审核通过。</p>
     *
     * @param userId 评论人，取自登录态
     * @param dto    评论入参（商品 + 正文）
     * @return 新建评论的视图对象（含初始审核状态，便于前端提示「审核中」）
     */
    CommentVO publishProductComment(Long userId, CommentPublishDTO dto);

    /**
     * 分页查询某商品下**已过审**的评论，按评论时间倒序。
     *
     * @param productId 商品主键
     * @param pageNum   页码，从 1 开始
     * @param pageSize  每页条数
     */
    IPage<CommentVO> pageProductComments(Long productId, long pageNum, long pageSize);

    /**
     * 批量查询「某用户对若干商品」的评论状态，供订单列表标记「审核中 / 已评价」。
     *
     * <p>只回「待审核 / 已通过」两种有效状态。从未评价、以及被驳回（未通过）的
     * 商品**不会出现在返回的 map 里** —— 这两种情况对调用方含义相同：还能再评价。</p>
     *
     * @param userId     评论人，取自登录态
     * @param productIds 商品主键集合
     * @return productId → 审核状态（待审核 / 已通过）；无有效评论的商品不在 map 中
     */
    Map<Long, String> commentStatusOf(Long userId, Collection<Long> productIds);
}
