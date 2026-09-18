package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 求购需求实体，对应表 t_demand。
 */
@Data
@TableName("t_demand")
public class Demand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 求购需求主键 */
    @TableId(value = "demand_id", type = IdType.AUTO)
    private Long demandId;

    /** 发布人（采购方或农户） */
    private Long publisherId;

    /** 求购品类 */
    private String category;

    /** 求购数量 */
    private Integer quantity;

    /** 计量单位 */
    private String unit;

    /** 期望单价 */
    private BigDecimal expectPrice;

    /** 需求补充说明 */
    private String description;

    /** 收货地区 */
    private String region;

    /** 需求有效期 */
    private LocalDateTime validUntil;

    /** 联系方式 */
    private String contact;

    /** 状态：1 有效，0 已关闭 */
    private Integer status;

    /** 发布时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
