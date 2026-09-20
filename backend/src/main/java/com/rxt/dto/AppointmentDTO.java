package com.rxt.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppointmentDTO {
    private Long expertId;           // 预约哪位专家
    private LocalDateTime apptTime;  // 预约时间
    private String apptTopic;        // 咨询主题
    private String remark;           // 备注
}