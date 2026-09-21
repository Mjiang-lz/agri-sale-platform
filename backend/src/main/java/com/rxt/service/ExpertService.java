package com.rxt.service;

import com.rxt.entity.Knowledge;
import java.time.LocalDateTime;
import java.util.List;
import com.rxt.dto.AppointmentDTO;

/**
 * 专家助力模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface ExpertService {

    /**
     * 按分类查询农业知识。
     */
    List<Knowledge> listKnowledge(String category);

    /**
     * 向专家提问。
     */
    Long askQuestion(Long askerId, Long expertId, String content);

    /**
     * 预约专家。
     */
    Long makeAppointment(Long farmerId, AppointmentDTO dto);

}
