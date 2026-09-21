package com.rxt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    // 列表接口，支持分类筛选 + 关键词搜索 + 分页
    @GetMapping("/list")
    public Result<Page<Knowledge>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(knowledgeService.pageQuery(category, keyword, page, size));
    }

    // 详情接口
    @GetMapping("/{id}")
    public Result<Knowledge> detail(@PathVariable Long id) {
        return Result.success(knowledgeService.getDetail(id));
    }
}