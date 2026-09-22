package com.rxt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.entity.Knowledge;
import java.util.List;

public interface KnowledgeService {
    Page<Knowledge> pageQuery(String category, String keyword, Integer page, Integer size);
    Knowledge getDetail(Long id);                    // 详情
}