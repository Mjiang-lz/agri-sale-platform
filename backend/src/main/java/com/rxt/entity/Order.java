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
 * 订单实体，对应表 t_order。
 */
@Data
@TableName("t_order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单主键 */
    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    /** 订单编号，对外展示 */
    private String orderNo;

    /** 采购方 */
    private Long buyerId;

    /** 农户（卖家） */
    private Long sellerId;

    /** 商品 */
    private Long productId;

    /** 购买数量 */
    private Integer quantity;

    /** 下单时单价快照 */
    private BigDecimal unitPrice;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 订单状态：待支付、待发货、待收货、已完成、已取消 */
    private String orderStatus;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliverTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 下单时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
