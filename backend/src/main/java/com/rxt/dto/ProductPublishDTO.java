package com.rxt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布 / 编辑商品入参。
 *
 * <p>发布与编辑共用一个 DTO：字段完全一致，拆成两个类只会让两边慢慢漂移。</p>
 *
 * <p><b>编辑按「部分更新」处理</b>：字段为 null 表示「本次不修改该字段」，要清空某个
 * 可选字段请传空字符串 {@code ""}（多图传空数组），不要传 null —— 这与 MyBatis-Plus
 * {@code updateById} 跳过 null 字段的口径保持一致。</p>
 *
 * <p><b>长度上限一律与 t_product 的列宽对齐</b>：注解比列宽宽松时，超长值会一路走到
 * JDBC 才抛 {@code Data too long for column}，被兜底成 500「系统繁忙」，联调时很难定位。
 * 改这里之前先核对 schema.sql 的列宽（productName 100 / category 30 / unit 10 /
 * imageUrl 255 / origin 50）。</p>
 */
@Data
public class ProductPublishDTO {

    /** 商品名称 */
    @NotBlank(message = "请填写商品名称")
    @Size(max = 100, message = "商品名称不能超过 100 字")
    private String productName;

    /** 商品品类（t_product.category 为 varchar(30)，上限必须一致） */
    @NotBlank(message = "请选择商品品类")
    @Size(max = 30, message = "商品品类不能超过 30 字")
    private String category;

    /** 单价 */
    @NotNull(message = "请填写单价")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    private BigDecimal price;

    /** 可售库存 */
    @NotNull(message = "请填写库存")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    /** 计量单位（斤、公斤、箱等）。t_product.unit 为 varchar(10)，上限必须一致 */
    @Size(max = 10, message = "计量单位不能超过 10 字")
    private String unit;

    /** 商品描述 */
    @Size(max = 500, message = "商品描述不能超过 500 字")
    private String description;

    /** 产地（到市，如 江西赣州） */
    @Size(max = 50, message = "产地不能超过 50 字")
    private String origin;

    /** 主图地址（列表页展示用），只接收上传后的 URL */
    @Size(max = 255, message = "图片地址不能超过 255 字")
    private String imageUrl;

    /**
     * 多图地址数组（详情页展示用），最多 9 张。
     *
     * <p>图片本体由独立的上传接口负责（走对象存储），这里只接收上传后的 URL 列表。</p>
     */
    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;
}
