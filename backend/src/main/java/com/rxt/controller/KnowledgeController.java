package com.rxt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.Result;
import com.rxt.dto.KnowledgeCreateDTO;
import com.rxt.entity.Knowledge;
import com.rxt.security.UserContext;
import com.rxt.service.KnowledgeService;
import jakarta.validation.Valid;
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

    /** 当前专家自己的知识投稿，含待审核、已通过、未通过全部状态。 */
    @GetMapping("/my")
    public Result<List<Knowledge>> myKnowledge() {
        return Result.success(knowledgeService.myKnowledge(UserContext.currentUserId()));
    }

    // 详情接口
    @GetMapping("/{id}")
    public Result<Knowledge> detail(@PathVariable Long id) {
        return Result.success(knowledgeService.getDetail(id));
    }

    /**
     * 专家新增农业知识。
     * 新内容写入 t_knowledge，审核状态固定为“待审核”，通过管理端知识审核后才会公开。
     */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid KnowledgeCreateDTO dto) {
        return Result.success(knowledgeService.createKnowledge(UserContext.currentUserId(), dto));
    }
}