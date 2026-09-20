package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.entity.Appointment;
import com.rxt.entity.Knowledge;
import com.rxt.entity.Question;
import com.rxt.mapper.AppointmentMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.QuestionMapper;
import com.rxt.service.ExpertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpertServiceImpl implements ExpertService {

    @Autowired private KnowledgeMapper knowledgeMapper;
    @Autowired private QuestionMapper questionMapper;
    @Autowired private AppointmentMapper appointmentMapper;

    @Override
    public List<Knowledge> listKnowledge(String category) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Knowledge::getCategory, category);
        }
        wrapper.orderByDesc(Knowledge::getCreateTime);
        return knowledgeMapper.selectList(wrapper);
    }

    @Override
    public Long askQuestion(Long askerId, Long expertId, String content) {
        Question q = new Question();
        q.setAskerId(askerId);
        q.setExpertId(expertId);
        q.setContent(content);
        q.setStatus("待回复");
        q.setAskTime(LocalDateTime.now());
        questionMapper.insert(q);
        return q.getQuestionId();
    }

    @Override
    public Long makeAppointment(Long farmerId, Long expertId, LocalDateTime apptTime, String topic) {
        Appointment app = new Appointment();
        app.setFarmerId(farmerId);
        app.setExpertId(expertId);
        app.setApptTime(apptTime);
        app.setApptTopic(topic);
        app.setApptStatus("待处理");
        appointmentMapper.insert(app);
        return app.getApptId();
    }
}