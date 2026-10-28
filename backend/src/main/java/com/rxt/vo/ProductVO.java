package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品列表 / 详情视图对象。
 *
 * <p><b>本 VO 有两种填充完整度</b>（与 {@link FinanceApplyVO} 同一手法，
 * 不填的字段由 {@code spring.jackson.default-property-inclusion=non_null}
 * 在序列化时直接省略，前端无需区分两种形态）：</p>
 * <ul>
 *   <li>前台列表（{@code GET /trade/products}）与商品详情里的推荐位：
 *       只填列表要用的字段，<b>不含</b> description / images ——
 *       一页 12 条各带一段 500 字描述，纯属把列表响应撑大；</li>
 *   <li>农户的「我的商品」（{@code GET /trade/my-products}）：额外填
 *       description 与 images，因为编辑表单要把这两项回显出来，
 *       否则农户点「编辑」会看到空的描述框，一保存就把原文案清掉了。</li>
 * </ul>
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

    /** 商品描述。仅「我的商品」列表填充，供编辑表单回显 */
    private String description;

    /** 多图地址数组。仅「我的商品」列表填充，供编辑表单回显 */
    private List<String> images;
}
