package com.rxt.service;

import com.rxt.dto.AppointmentDTO;
import com.rxt.vo.AppointmentVO;

import java.util.List;

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

    /**
     * 我的预约列表（REQ-11「预约状态与结果通知」的查询侧）。
     *
     * @param farmerId 农户用户主键，取自登录态；不接受按他人 id 查询
     * @return 该农户的全部预约，按提交时间倒序
     */
    List<AppointmentVO> myAppointments(Long farmerId);
}
