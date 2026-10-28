package com.rxt.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * 收货地址主键。下单时必填。
     *
     * <p>必填的理由：订单一旦生成就要进入「农户发货」环节，
     * 没有收货人的订单农户无法处理，只能挂着 —— 与其让脏订单进库再人工清，
     * 不如在入口挡住。Service 会再校验一次地址归属，
     * 防止传别人的 addressId 把货发到他人地址上。</p>
     */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /**
     * 买家备注（如「工作日送达」），可选。
     *
     * <p>上限与 t_order.order_remark 的列宽 200 对齐：注解比列宽宽松时，
     * 超长值会一路走到 JDBC 才抛 {@code Data too long for column}，
     * 被兜底成 500「系统繁忙」，联调时很难定位。</p>
     */
    @Size(max = 200, message = "备注不能超过 200 字")
    private String orderRemark;
}
