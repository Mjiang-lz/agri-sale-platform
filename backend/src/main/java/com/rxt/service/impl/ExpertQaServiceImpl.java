package com.rxt.service.impl;

import com.rxt.dto.QuestionDTO;
import com.rxt.dto.AnswerDTO;
import com.rxt.entity.Question;
import com.rxt.entity.Answer;
import com.rxt.mapper.QuestionMapper;
import com.rxt.mapper.AnswerMapper;
import com.rxt.service.ExpertQaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ExpertQaServiceImpl implements ExpertQaService {

    @Autowired private QuestionMapper questionMapper;
    @Autowired private AnswerMapper answerMapper;

    @Override
    public void askQuestion(Long askerId, QuestionDTO dto) {
        Question q = new Question();
        q.setAskerId(askerId);
        q.setExpertId(dto.getExpertId());
        q.setContent(dto.getContent());
        q.setStatus("待回复"); // 初始状态
        q.setAskTime(LocalDateTime.now());
        questionMapper.insert(q);
    }

    @Override
    public void answerQuestion(Long expertId, AnswerDTO dto) {
        // 1. 新增回答
        Answer a = new Answer();
        a.setQuestionId(dto.getQuestionId());
        a.setExpertId(expertId);
        a.setContent(dto.getContent());
        a.setAnswerTime(LocalDateTime.now());
        answerMapper.insert(a);

        Question q = questionMapper.selectById(dto.getQuestionId());
        if(q != null){
            q.setStatus("已回复");
            questionMapper.updateById(q);
        }
    }
}