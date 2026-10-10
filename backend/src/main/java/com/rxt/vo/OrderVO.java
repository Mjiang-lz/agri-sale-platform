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

    /**
     * 收货人姓名（下单时快照）。
     *
     * <p>历史订单（2026-10-10 之前创建的）没有收货信息，这三项会是 null，
     * 前端要按「未填写收货信息」展示，不要渲染成空白行或字符串 "null"。</p>
     */
    private String consignee;

    /** 收货人手机号（下单时快照） */
    private String consigneePhone;

    /** 收货地址全文（下单时快照） */
    private String receiverAddress;

    /** 买家备注，可选 */
    private String orderRemark;

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
