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

    /** 当前专家自己的全部知识投稿。 */
    List<Knowledge> myKnowledge(Long userId);
}