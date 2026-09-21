package com.rxt.service;

import com.rxt.dto.AppointmentDTO;

/**
 * 专家助力模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface ExpertService {


    /**
     * 预约专家。
     */
    Long makeAppointment(Long farmerId, AppointmentDTO dto);

}
