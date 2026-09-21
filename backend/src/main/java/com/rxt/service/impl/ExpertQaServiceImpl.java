package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.dto.AnswerDTO;
import com.rxt.dto.QuestionDTO;
import com.rxt.entity.Answer;
import com.rxt.entity.ExpertInfo;
import com.rxt.entity.Question;
import com.rxt.mapper.AnswerMapper;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.QuestionMapper;
import com.rxt.service.ExpertQaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpertQaServiceImpl implements ExpertQaService {

    @Autowired private QuestionMapper questionMapper;
    @Autowired private AnswerMapper answerMapper;
    @Autowired private ExpertInfoMapper expertInfoMapper;

    @Override
    public void askQuestion(Long askerId, QuestionDTO dto) {
        if (dto.getExpertId() != null) {
            ExpertInfo expert = expertInfoMapper.selectById(dto.getExpertId());
            if (expert == null) {
                throw new RuntimeException("指定的专家不存在");
            }
        }
        Question q = new Question();
        q.setAskerId(askerId);
        q.setExpertId(dto.getExpertId());
        q.setContent(dto.getContent());
        q.setStatus("待回复");
        q.setAskTime(LocalDateTime.now());
        questionMapper.insert(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void answerQuestion(Long userId, AnswerDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new RuntimeException("仅专家可回答问题");
        }

        Question q = questionMapper.selectById(dto.getQuestionId());
        if (q == null) {
            throw new RuntimeException("问题不存在");
        }
        if (q.getExpertId() != null && !q.getExpertId().equals(expert.getExpertId())) {
            throw new RuntimeException("该问题已指定其他专家");
        }

        Answer a = new Answer();
        a.setQuestionId(dto.getQuestionId());
        a.setExpertId(expert.getExpertId());
        a.setContent(dto.getContent());
        a.setAnswerTime(LocalDateTime.now());
        answerMapper.insert(a);

        LambdaUpdateWrapper<Question> uw = new LambdaUpdateWrapper<>();
        uw.eq(Question::getQuestionId, dto.getQuestionId())
                .set(Question::getStatus, "已回复");
        questionMapper.update(null, uw);
    }

    @Override
    public List<Question> listMyQuestions(Long userId) {
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getAskerId, userId)
                .orderByDesc(Question::getAskTime));
    }

    @Override
    public List<Question> listPendingQuestions(Long userId) {
        ExpertInfo expert = expertInfoMapper.selectOne(new LambdaQueryWrapper<ExpertInfo>()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new RuntimeException("仅专家可查看待回复问题");
        }
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, "待回复")
                .and(w -> w.eq(Question::getExpertId, expert.getExpertId())
                        .or().isNull(Question::getExpertId))
                .orderByDesc(Question::getAskTime));
    }

    @Override
    public List<Answer> listAnswersByQuestionId(Long questionId) {
        return answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                .eq(Answer::getQuestionId, questionId)
                .orderByAsc(Answer::getAnswerTime));
    }
}