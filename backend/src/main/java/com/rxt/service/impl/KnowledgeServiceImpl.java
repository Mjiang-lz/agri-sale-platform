package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.KnowledgeCreateDTO;
import com.rxt.entity.Comment;
import com.rxt.entity.ExpertInfo;
import com.rxt.entity.Knowledge;
import com.rxt.mapper.CommentMapper;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String AUDIT_PENDING = "待审核";
    private static final String AUDIT_APPROVED = "已通过";

    /** t_comment.target_type 的知识取值。与 AuditServiceImpl.TYPE_KNOWLEDGE 同一个词表 */
    private static final String COMMENT_TARGET_KNOWLEDGE = "KNOWLEDGE";

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private ExpertInfoMapper expertInfoMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Override
    public Page<Knowledge> pageQuery(String category, String keyword, Integer page, Integer size) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Knowledge::getCategory, category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Knowledge::getTitle, keyword)
                    .or().like(Knowledge::getContent, keyword));
        }
        wrapper.eq(Knowledge::getAuditStatus, AUDIT_APPROVED)
                .orderByDesc(Knowledge::getCreateTime);

        // B2 修复：不传分页参数时返回全量，兼容前端内存切片
        if (page == null || size == null) {
            List<Knowledge> list = knowledgeMapper.selectList(wrapper);
            Page<Knowledge> allPage = new Page<>(1, list.size());
            allPage.setRecords(list);
            return allPage;
        }
        return knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createKnowledge(Long userId, KnowledgeCreateDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可发布农业知识");
        }

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(dto.getTitle().trim());
        knowledge.setCategory(dto.getCategory().trim());
        knowledge.setContent(dto.getContent().trim());
        knowledge.setImageUrl(dto.getImageUrl());
        knowledge.setAuthorId(expert.getExpertId());
        knowledge.setAuditStatus(AUDIT_PENDING);
        knowledge.setViewCount(0);
        knowledgeMapper.insert(knowledge);
        return knowledge.getKnowledgeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(Long userId, Long knowledgeId, KnowledgeCreateDTO dto) {
        // 归属校验的先决条件：当前用户必须有一份专家档案。
        // author_id 存的是 t_expert.expert_id（不是 user_id），所以必须先换算。
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可编辑农业知识");
        }

        Knowledge exist = knowledgeMapper.selectById(knowledgeId);
        if (exist == null) {
            // 不存在与不属于自己刻意给不同的码：同项目 TradeServiceImpl.requireOwnProduct
            // 也是这么分的 —— 报「不存在」会让作者以为自己那条丢了。
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "知识不存在");
        }
        if (!exist.getAuthorId().equals(expert.getExpertId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "只能编辑自己发布的知识");
        }

        Knowledge update = new Knowledge();
        update.setKnowledgeId(knowledgeId);
        update.setTitle(dto.getTitle().trim());
        update.setCategory(dto.getCategory().trim());
        update.setContent(dto.getContent().trim());
        update.setImageUrl(dto.getImageUrl());

        // 改动一律重新送审。与 TradeServiceImpl.updateProduct 同一口径（那边是
        // 「改了内容类字段就回待审核」），差别只在于：商品有价格/库存这类经营信息
        // 可以免审，而知识的所有可编辑字段都是内容，所以这里不做字段级判断。
        // 已通过的知识一旦被编辑，会立刻从前台列表消失（pageQuery 只查 已通过），
        // 这是有意的：改正文必须重新过审，否则就是一条绕过审核的改稿通道。
        update.setAuditStatus(AUDIT_PENDING);

        knowledgeMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long userId, Long knowledgeId) {
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可删除农业知识");
        }

        Knowledge exist = knowledgeMapper.selectById(knowledgeId);
        if (exist == null) {
            // 与 updateKnowledge 同一口径：404 与 403 分开报。
            // 报「不存在」会让作者以为自己那条丢了，报 403 才是"它在，但不是你的"。
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "知识不存在");
        }
        if (!exist.getAuthorId().equals(expert.getExpertId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "只能删除自己发布的知识");
        }

        // 先清评论再删正文。t_comment.target_id 是多态关联、没有外键，所以不清也不会
        // 撞约束 —— 但会留下「评论还在、被评论的知识没了」的孤儿行：从此查不到、
        // 也没人再能删掉它。评论模块目前只写 PRODUCT 评论，这条清理覆盖的是历史/种子数据。
        commentMapper.delete(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, COMMENT_TARGET_KNOWLEDGE)
                .eq(Comment::getTargetId, knowledgeId));

        knowledgeMapper.deleteById(knowledgeId);

        // t_audit 流水刻意不删（理由见 KnowledgeService#deleteKnowledge）：
        // 审核确实发生过，删掉它等于篡改管理员的工作量统计。
    }

    @Override
    public List<Knowledge> myKnowledge(Long userId) {
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可查看自己的知识投稿");
        }
        return knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getAuthorId, expert.getExpertId())
                .orderByDesc(Knowledge::getCreateTime));
    }

    @Override
    public Knowledge getDetail(Long id) {
        Knowledge k = knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeId, id)
                .eq(Knowledge::getAuditStatus, AUDIT_APPROVED));
        if (k == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "知识不存在或未通过审核");
        }
        return k;
    }
}