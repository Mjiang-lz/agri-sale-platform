package com.rxt.dto;
import lombok.Data;

@Data
public class LoanGroupDTO {
    private String groupName;   // 小组名称
    private String description; // 描述
    // 根据 LoanGroup 实体类补充其他建组需要的字段
}