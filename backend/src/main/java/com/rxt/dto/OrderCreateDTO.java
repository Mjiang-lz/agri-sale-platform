package com.rxt.dto;

import jakarta.validation.constraints.Min;
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

    /**
     * 购买数量。
     *
     * <p>{@code @Min(1)} 不是可有可无的装饰：扣减库存的 SQL 是
     * {@code stock = stock - quantity WHERE stock >= quantity}，
     * 若传入负数，{@code stock >= -5} 恒成立，库存会被「减负数」而<b>反向增加</b>，
     * 订单金额也会变成负数。传 0 则会生成一张金额为 0 的空订单。</p>
     */
    @NotNull(message = "请填写购买数量")
    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity;
}
