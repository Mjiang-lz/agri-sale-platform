package com.rxt.service.impl;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.AppointmentDTO;
import com.rxt.entity.Appointment;
import com.rxt.entity.ExpertInfo;
import com.rxt.mapper.AppointmentMapper;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.QuestionMapper;
import com.rxt.service.ExpertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpertServiceImpl implements ExpertService {

    @Autowired private KnowledgeMapper knowledgeMapper;
    @Autowired private QuestionMapper questionMapper;
    @Autowired private AppointmentMapper appointmentMapper;
    @Autowired private ExpertInfoMapper expertInfoMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long makeAppointment(Long farmerId, AppointmentDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectById(dto.getExpertId());
        if (expert == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "专家不存在");
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