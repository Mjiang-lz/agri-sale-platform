package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
// 注意：context-path 已是 /api，这里只写 /knowledge，写成 /api/knowledge 会变成 /api/api/knowledge
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    // 列表接口，支持按分类筛选
    @GetMapping("/list")
    public Result<List<Knowledge>> list(@RequestParam(required = false) String category) {
        return Result.success(knowledgeService.listByCategory(category));
    }

    // 详情接口
    @GetMapping("/{id}")
    public Result<Knowledge> detail(@PathVariable Long id) {
        return Result.success(knowledgeService.getDetail(id));
    }
}