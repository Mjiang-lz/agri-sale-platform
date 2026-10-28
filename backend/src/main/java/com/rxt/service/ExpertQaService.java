package com.rxt.service;

import com.rxt.dto.QuestionDTO;
import com.rxt.dto.AnswerDTO;
import com.rxt.entity.Answer;
import com.rxt.entity.Question;

import java.util.List;

public interface ExpertQaService {
    void askQuestion(Long askerId, QuestionDTO dto);
    void answerQuestion(Long userId, AnswerDTO dto);

    List<Question> listMyQuestions(Long userId);

    List<Question> listPendingQuestions(Long userId);

    List<Answer> listAnswersByQuestionId(Long questionId);
}