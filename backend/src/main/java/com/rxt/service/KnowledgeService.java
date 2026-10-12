package com.rxt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.dto.KnowledgeCreateDTO;
import com.rxt.entity.Knowledge;
import java.util.List;

public interface KnowledgeService {
    Page<Knowledge> pageQuery(String category, String keyword, Integer page, Integer size);
    Knowledge getDetail(Long id);                    // 详情

    /** 专家新增知识，返回知识主键。 */
    Long createKnowledge(Long userId, KnowledgeCreateDTO dto);

    /**
     * 专家编辑自己的知识投稿。
     *
     * <p>对应《软件需求规格说明书》「个人信息 → 专家管理」的处理项
     * <b>「新增与编辑知识」</b>。归属校验用 {@code t_knowledge.author_id}
     * 与当前用户的 {@code t_expert.expert_id} 比对；编辑成功后审核状态
     * 一律回到「待审核」，理由见 {@code docs/接口约定-M5知识投稿.md} §5。</p>
     *
     * @param userId      当前登录用户主键，取自登录态
     * @param knowledgeId 待编辑的知识主键
     * @param dto         新内容，字段约束与新增一致
     */
    void updateKnowledge(Long userId, Long knowledgeId, KnowledgeCreateDTO dto);

    /**
     * 专家删除自己的知识投稿。
     *
     * <p><b>为什么可以物理删，而商品不行</b>：全库没有任何一条外键指向
     * {@code t_knowledge}（{@code author_id} 那条是它指出去的外键），知识也不参与
     * 下单链路 —— 所以删行不会留下悬空引用，不需要像商品那样"被引用就改判下架"。</p>
     *
     * <p><b>副作用与处置</b>：删前会先清掉该知识的评论（{@code t_comment.target_id}
     * 是多态关联、没有外键，不清就会留下"评论还在、被评论的知识没了"的孤儿行）；
     * {@code t_audit} 的审核流水<b>刻意保留</b> —— 审核确实发生过，删掉它等于篡改
     * 审核工作量统计。代价是审核列表里会出现一条取不到标题的流水，由
     * {@code AuditServiceImpl.fillTargetInfo} 标成「（内容已删除）」而不是留空。</p>
     *
     * @param userId      当前登录用户主键，取自登录态
     * @param knowledgeId 待删除的知识主键
     */
    void deleteKnowledge(Long userId, Long knowledgeId);

    /** 当前专家自己的全部知识投稿。 */
    List<Knowledge> myKnowledge(Long userId);
}