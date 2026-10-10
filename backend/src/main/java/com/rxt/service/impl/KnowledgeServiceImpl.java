package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.KnowledgeCreateDTO;
import com.rxt.entity.ExpertInfo;
import com.rxt.entity.Knowledge;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private ExpertInfoMapper expertInfoMapper;

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
        wrapper.eq(Knowledge::getAuditStatus, "已通过")
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
        knowledge.setAuditStatus("待审核");
        knowledge.setViewCount(0);
        knowledgeMapper.insert(knowledge);
        return knowledge.getKnowledgeId();
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
                .eq(Knowledge::getAuditStatus, "已通过"));
        if (k == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "知识不存在或未通过审核");
        }
        return k;
    }
}