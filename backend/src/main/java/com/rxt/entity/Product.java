package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 商品货源实体，对应表 t_product。
 *
 * <p>注意 {@code autoResultMap = true}：{@code images} 是 JSON 列，靠
 * {@link JacksonTypeHandler} 在 String 与 {@code List<String>} 之间转换。
 * 该处理器必须出现在 resultMap 里才会生效，而 MyBatis-Plus 只在这个开关
 * 打开时才生成带 typeHandler 的 resultMap —— 少了它，查询会把 JSON 串
 * 直接塞给 {@code List<String>} 字段并抛类型转换异常。</p>
 */
@Data
@TableName(value = "t_product", autoResultMap = true)
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

    /** 商品主图地址（对象存储 URL），列表页展示用 */
    private String imageUrl;

    /**
     * 多图地址数组，详情页展示用（对应 JSON 列 images）。
     *
     * <p>保留 imageUrl 作为「主图」而不把两者合并，是为了不动商品列表接口 ——
     * 列表页只需要一张图，改成数组会让现有接口和前端一起返工。</p>
     */
    @TableField(value = "images", typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 商品描述 */
    private String description;

    /** 产地（到市，如 江西赣州）。历史商品可能为 null */
    private String origin;

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
