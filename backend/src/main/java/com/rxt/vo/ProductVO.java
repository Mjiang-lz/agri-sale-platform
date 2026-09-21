package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品列表 / 详情视图对象。
 */
@Data
public class ProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;
    private String productName;
    private String category;
    private BigDecimal price;
    private Integer stock;
    private String unit;
    private String imageUrl;

    /** 产地（到市，如 江西赣州）。列表页展示并提供给前端做筛选回显 */
    private String origin;

    private Long sellerId;
    private String auditStatus;
    private Integer status;
}
