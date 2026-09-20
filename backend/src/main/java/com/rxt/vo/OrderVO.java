package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象。
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单主键 */
    private Long orderId;

    /** 订单编号（对外展示） */
    private String orderNo;

    /** 采购方（买家） */
    private Long buyerId;

    /** 农户（卖家） */
    private Long sellerId;

    /** 商品 */
    private Long productId;

    /** 商品名称（关联 t_product 补齐，商品被删时可能为 null） */
    private String productName;

    /** 商品主图 */
    private String imageUrl;

    /** 计价单位 */
    private String unit;

    /** 购买数量 */
    private Integer quantity;

    /** 下单时单价快照 */
    private BigDecimal unitPrice;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 订单状态：待支付、待发货、待收货、已完成、已取消 */
    private String orderStatus;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliverTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 下单时间 */
    private LocalDateTime createTime;
}
