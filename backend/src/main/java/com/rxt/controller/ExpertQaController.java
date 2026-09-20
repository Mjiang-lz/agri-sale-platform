package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.QuestionDTO;
import com.rxt.dto.AnswerDTO;
import com.rxt.service.ExpertQaService;
import com.rxt.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expert/qa")
public class ExpertQaController {

    @Autowired
    private ExpertQaService expertQaService;

    @PostMapping("/ask")
    public Result<Void> ask(@RequestBody QuestionDTO dto) {
        Long askerId = UserContext.currentUserId();
        expertQaService.askQuestion(askerId, dto);
        return Result.success();
    }

    @PostMapping("/answer")
    public Result<Void> answer(@RequestBody AnswerDTO dto) {
        Long expertId = UserContext.currentUserId();
        expertQaService.answerQuestion(expertId, dto);
        return Result.success();
    }
}