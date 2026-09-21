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
 * <p>发布与编辑共用一个 DTO：编辑走整体替换语义，前端提交表单时本来就会带上
 * 全部字段，再拆成两个类只会让两边字段慢慢漂移。</p>
 */
@Data
public class ProductPublishDTO {

    /** 商品名称 */
    @NotBlank(message = "请填写商品名称")
    @Size(max = 100, message = "商品名称不能超过 100 字")
    private String productName;

    /** 商品品类 */
    @NotBlank(message = "请选择商品品类")
    @Size(max = 50, message = "商品品类不能超过 50 字")
    private String category;

    /** 单价 */
    @NotNull(message = "请填写单价")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    private BigDecimal price;

    /** 可售库存 */
    @NotNull(message = "请填写库存")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    /** 计量单位（斤、公斤、箱等） */
    @Size(max = 20, message = "计量单位不能超过 20 字")
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
