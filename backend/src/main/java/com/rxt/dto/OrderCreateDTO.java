package com.rxt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单入参。
 */
@Data
public class OrderCreateDTO {

    /** 商品主键 */
    @NotNull(message = "请选择商品")
    private Long productId;

    /** 购买数量 */
    @NotNull(message = "请填写购买数量")
    private Integer quantity;
}
