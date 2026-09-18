package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车条目视图对象。
 *
 * <p>在 t_cart 基础上补齐商品名称、图片、单价、单位、剩余库存，
 * 便于前端购物车页面直接渲染，无需再逐个调商品接口。</p>
 */
@Data
public class CartVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车条目主键 */
    private Long cartId;

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品图片 */
    private String imageUrl;

    /** 计价单位（斤/箱/件…） */
    private String unit;

    /** 商品当前单价 */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 条目金额小计（单价 × 数量） */
    private BigDecimal totalAmount;

    /** 商品剩余库存，供前端判断是否可以继续加量 */
    private Integer stock;

    /** 商品状态：1 上架，0 下架（下架商品前端应置灰） */
    private Integer status;

    /** 加入购物车时间 */
    private LocalDateTime createTime;
}
