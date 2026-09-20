package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
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