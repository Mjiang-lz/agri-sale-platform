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

    /** 当前专家自己的全部知识投稿。 */
    List<Knowledge> myKnowledge(Long userId);
}