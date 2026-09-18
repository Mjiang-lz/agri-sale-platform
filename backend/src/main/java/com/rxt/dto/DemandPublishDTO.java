package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布 / 编辑求购需求入参。
 */
@Data
public class DemandPublishDTO {

    /** 求购品类 */
    @NotBlank(message = "请填写求购品类")
    private String category;

    /** 求购数量 */
    @NotNull(message = "请填写求购数量")
    private Integer quantity;

    /** 计量单位 */
    private String unit;

    /** 期望单价 */
    private BigDecimal expectPrice;

    /** 需求补充说明 */
    private String description;

    /** 收货地区 */
    @NotBlank(message = "请填写收货地区")
    private String region;

    /** 需求有效期 */
    @NotNull(message = "请填写需求有效期")
    private LocalDateTime validUntil;

    /** 联系方式 */
    @NotBlank(message = "请填写联系方式")
    private String contact;
}
