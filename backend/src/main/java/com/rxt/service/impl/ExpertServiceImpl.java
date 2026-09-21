package com.rxt.service.impl;

import com.rxt.dto.AppointmentDTO;
import com.rxt.entity.ExpertInfo;
import com.rxt.mapper.ExpertInfoMapper;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired private ExpertInfoMapper expertInfoMapper;

    @Override
    public List<Knowledge> listKnowledge(String category) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Knowledge::getCategory, category);
        }
        wrapper.eq(Knowledge::getAuditStatus, "已通过");
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
    @Transactional(rollbackFor = Exception.class)
    public Long makeAppointment(Long farmerId, AppointmentDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectById(dto.getExpertId());
        if (expert == null) {
            throw new RuntimeException("专家不存在");
        }

        Appointment app = new Appointment();
        app.setFarmerId(farmerId);
        app.setExpertId(dto.getExpertId());
        app.setApptTime(dto.getApptTime());
        app.setApptTopic(dto.getApptTopic());
        app.setRemark(dto.getRemark());
        app.setApptStatus("待处理");
        appointmentMapper.insert(app);
        return app.getApptId();
    }
}