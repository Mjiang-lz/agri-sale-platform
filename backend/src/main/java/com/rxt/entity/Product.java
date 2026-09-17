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
 * 商品货源实体，对应表 t_product。
 */
@Data
@TableName("t_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品主键 */
    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品品类 */
    private String category;

    /** 单价，下单时作为价格快照写入订单 */
    private BigDecimal price;

    /** 可售库存 */
    private Integer stock;

    /** 计量单位（斤、公斤、箱等） */
    private String unit;

    /** 商品图片地址（对象存储 URL） */
    private String imageUrl;

    /** 商品描述 */
    private String description;

    /** 发布农户 */
    private Long sellerId;

    /** 审核状态：待审核、已通过、未通过 */
    private String auditStatus;

    /** 上下架状态：1 上架，0 下架 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
