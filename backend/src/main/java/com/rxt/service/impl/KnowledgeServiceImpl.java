package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.entity.Knowledge;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Override
    public List<Knowledge> listByCategory(String category) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Knowledge::getCategory, category);
        }
        wrapper.eq(Knowledge::getAuditStatus, "已通过");
        wrapper.orderByDesc(Knowledge::getCreateTime);
        return knowledgeMapper.selectList(wrapper);
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