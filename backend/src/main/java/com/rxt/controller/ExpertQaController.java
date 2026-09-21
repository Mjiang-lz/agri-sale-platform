package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.AnswerDTO;
import com.rxt.dto.QuestionDTO;
import com.rxt.entity.Answer;
import com.rxt.entity.Question;
import com.rxt.security.UserContext;
import com.rxt.service.ExpertQaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expert/qa")
public class ExpertQaController {

    @Autowired
    private ExpertQaService expertQaService;

    @PostMapping("/ask")
    public Result<Void> ask(@RequestBody @Valid QuestionDTO dto) {
        Long askerId = UserContext.currentUserId();
        expertQaService.askQuestion(askerId, dto);
        return Result.success();
    }

    @PostMapping("/answer")
    public Result<Void> answer(@RequestBody @Valid AnswerDTO dto) {
        Long userId = UserContext.currentUserId();
        expertQaService.answerQuestion(userId, dto);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<Question>> myQuestions() {
        return Result.success(expertQaService.listMyQuestions(UserContext.currentUserId()));
    }

    @GetMapping("/pending")
    public Result<List<Question>> pending() {
        return Result.success(expertQaService.listPendingQuestions(UserContext.currentUserId()));
    }

    @GetMapping("/list")
    public Result<List<Answer>> listByQuestion(@RequestParam Long questionId) {
        return Result.success(expertQaService.listAnswersByQuestionId(questionId));
    }
}