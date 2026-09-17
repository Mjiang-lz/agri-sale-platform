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
 * 购物车实体，对应表 t_cart。
 */
@Data
@TableName("t_cart")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车条目主键 */
    @TableId(value = "cart_id", type = IdType.AUTO)
    private Long cartId;

    /** 所属采购方 */
    private Long buyerId;

    /** 商品 */
    private Long productId;

    /** 数量 */
    private Integer quantity;

    /** 条目金额合计 */
    private BigDecimal totalAmount;

    /** 加入时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
