package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.ExpertService;
import com.rxt.security.UserContext; // 按你实际的导包路径为准
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/expert")
public class ExpertController {

    @Autowired
    private ExpertService expertService;

    @GetMapping("/knowledge/list")
    public Result<List<Knowledge>> listKnowledge(@RequestParam(required = false) String category) {
        return Result.success(expertService.listKnowledge(category));
    }

    @PostMapping("/ask")
    public Result<Long> ask(@RequestParam Long expertId, @RequestParam String content) {
        Long askerId = UserContext.currentUserId();
        Long questionId = expertService.askQuestion(askerId, expertId, content);
        return Result.success(questionId);
    }

    @PostMapping("/appointment/submit")
    public Result<Long> submit(@RequestParam Long expertId,
                               @RequestParam String apptTime,
                               @RequestParam String topic) {
        Long farmerId = UserContext.currentUserId();
        LocalDateTime time = LocalDateTime.parse(apptTime);
        Long apptId = expertService.makeAppointment(farmerId, expertId, time, topic);
        return Result.success(apptId);
    }
}