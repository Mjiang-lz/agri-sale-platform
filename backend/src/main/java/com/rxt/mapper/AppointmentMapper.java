package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专家预约数据访问接口（t_appointment）。
 */
@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
