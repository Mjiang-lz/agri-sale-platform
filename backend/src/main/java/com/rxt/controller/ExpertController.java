package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.security.UserContext;
import com.rxt.service.ExpertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专家助力接口。基础路径 /api/expert。
 */
@RestController
@RequestMapping("/expert")
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertService expertService;

    /**
     * 按分类查询农业知识。
     */
    @GetMapping("/knowledge")
    public Result<List<Knowledge>> listKnowledge(@RequestParam(required = false) String category) {
        return Result.success(expertService.listKnowledge(category));
    }

    /**
     * 向专家提问。expertId 为空表示公开提问，任何专家可认领。
     */
    @PostMapping("/question")
    public Result<Long> askQuestion(@RequestParam(required = false) Long expertId,
                                    @RequestParam String content) {
        return Result.success(expertService.askQuestion(UserContext.currentUserId(), expertId, content));
    }
}
