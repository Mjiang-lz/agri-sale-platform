package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情视图对象（REQ-06）。
 *
 * <p>一个对象承载三块内容：商品本身的全字段、发布农户的公开信息、
 * 该商品的已过审评论首页。这样买家端详情页首屏一次请求即可渲染完，
 * 不需要「商品 → 农户 → 评论」三次往返。</p>
 *
 * <p><b>刻意不返回的字段：</b></p>
 * <ul>
 *   <li>{@code update_time} —— 对买家没有展示意义，属内部字段。</li>
 *   <li>农户档案的 {@code credit_record} —— 里面含「意向额度 10 万元」这类
 *       经营与融资信息，只应给审核员和融资模块看。详情接口是面向买家的，
 *       带出去等于泄露农户的融资意向。</li>
 * </ul>
 */
@Data
public class ProductDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===================== 商品本身 =====================

    /** 商品主键 */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品品类 */
    private String category;

    /** 单价 */
    private BigDecimal price;

    /** 当前库存 */
    private Integer stock;

    /** 计量单位（斤、公斤、箱等） */
    private String unit;

    /** 主图地址，列表页与详情页首图都用它 */
    private String imageUrl;

    /** 多图地址数组，详情页图集用。历史商品可能为空数组或 null */
    private List<String> images;

    /** 商品描述 */
    private String description;

    /** 产地（到市，如 江西赣州） */
    private String origin;

    /**
     * 审核状态。
     *
     * <p>本接口只返回「已通过」的商品，所以这里恒为「已通过」。保留该字段
     * 一是与列表接口 {@link ProductVO} 字段保持一致，二是后续「农户预览自己
     * 商品」的场景可以直接复用本 VO，不必再建一个。</p>
     */
    private String auditStatus;

    /** 上下架状态：1 上架，0 下架。已下架时详情仍可查看（如从历史订单进入），由前端提示 */
    private Integer status;

    /** 上架时间 */
    private LocalDateTime createTime;

    // ===================== 发布农户 =====================

    /** 发布农户用户主键 */
    private Long sellerId;

    /** 农户姓名 */
    private String sellerName;

    /** 农户头像地址 */
    private String sellerAvatarUrl;

    /** 农户主营农产品（来自 t_farmer_profile） */
    private String sellerMainProduct;

    /** 农户经营规模描述（来自 t_farmer_profile） */
    private String sellerScaleDesc;

    // ===================== 评论 =====================

    /** 该商品已过审评论总数 */
    private Long commentTotal;

    /** 已过审评论首页，按评论时间倒序。翻页请调 GET /api/comment/product/list */
    private List<CommentVO> comments;
}
