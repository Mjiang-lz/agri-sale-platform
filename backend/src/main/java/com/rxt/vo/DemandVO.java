package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 求购需求列表 / 详情视图对象。
 */
@Data
public class DemandVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 求购需求主键 */
    private Long demandId;

    /** 发布人主键 */
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
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
