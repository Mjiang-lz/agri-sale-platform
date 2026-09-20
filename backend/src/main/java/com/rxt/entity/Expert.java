package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("t_expert")
public class Expert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;         // 专家姓名
    private String field;        // 领域（如：种植、养殖）
    private String introduction; // 简介
    @TableLogic
    private Integer deleted;
}