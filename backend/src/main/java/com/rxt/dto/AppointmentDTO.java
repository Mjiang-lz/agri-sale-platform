package com.rxt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppointmentDTO {
    @NotNull(message = "expertId 不能为空")
    private Long expertId;

    @NotNull(message = "预约时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd[' ']['T']HH:mm:ss")
    private LocalDateTime apptTime;

    @NotBlank(message = "咨询主题不能为空")
    private String apptTopic;

    private String remark;
}