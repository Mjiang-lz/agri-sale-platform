package com.rxt.service;

import com.rxt.dto.QuestionDTO;
import com.rxt.dto.AnswerDTO;

public interface ExpertQaService {
    void askQuestion(Long askerId, QuestionDTO dto);
    void answerQuestion(Long expertId, AnswerDTO dto);
}