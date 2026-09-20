package com.rxt.service;

import com.rxt.entity.Knowledge;
import java.util.List;

public interface KnowledgeService {
    List<Knowledge> listByCategory(String category); // 列表
    Knowledge getDetail(Long id);                    // 详情
}