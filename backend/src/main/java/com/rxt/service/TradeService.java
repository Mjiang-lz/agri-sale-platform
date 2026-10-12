package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.dto.ProductPublishDTO;
import com.rxt.vo.CartVO;
import com.rxt.vo.OrderVO;
import com.rxt.vo.ProductDetailVO;
import com.rxt.vo.ProductVO;

import java.math.BigDecimal;

/**
 * 农产品交易模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface TradeService {

    /**
     * 删除商品的结果：整行已物理删除。
     *
     * <p>与 {@link #DELETE_RESULT_OFF_SHELF} 一起构成 {@link #deleteProduct} 的返回值契约。
     * 前端 {@code request.js} 会把 {@code Result.data} 原样透出（{@code message} 被丢弃），
     * 所以「到底删掉了还是只下架了」只能靠这个值区分，不能靠文案。</p>
     */
    String DELETE_RESULT_DELETED = "DELETED";

    /**
     * 删除商品的结果：因已被订单引用，改判为下架。
     *
     * <p>依据 SRS「我的发布 · 例外流程」：删除已被订单引用的商品时，
     * 系统改为执行下架并提示不可删除的原因。</p>
     */
    String DELETE_RESULT_OFF_SHELF = "OFF_SHELF";

    /**
     * 分页检索商品货源，各条件可任意组合（都为 null 时返回全部在架商品）。
     *
     * @param keyword  商品名称模糊匹配
     * @param category 品类精确匹配
     * @param minPrice 价格下限（含）
     * @param maxPrice 价格上限（含）
     * @param origin   产地模糊匹配（传「赣州」或「江西」都能命中）
     */
    IPage<ProductVO> pageProducts(long pageNum, long pageSize, String keyword,
                                  String category, BigDecimal minPrice, BigDecimal maxPrice,
                                  String origin);

    /**
     * 发布商品。仅农户可操作，新商品进入待审核队列且默认下架。
     *
     * @param sellerId 发布农户（取自登录态）
     * @return 新商品主键
     */
    Long publishProduct(Long sellerId, ProductPublishDTO dto);

    /**
     * 编辑商品。仅商品归属农户可操作。
     *
     * <p>内容类字段（名称 / 品类 / 描述 / 产地 / 图片）任一变更会重新送审并下架；
     * 只改价格、库存、单位不触发重新审核 —— 这几个是经营行为，不是内容变更。</p>
     */
    void updateProduct(Long sellerId, Long productId, ProductPublishDTO dto);

    /**
     * 商品上下架。仅商品归属农户可操作；未审核通过的商品不允许上架。
     *
     * @param status 1 上架，0 下架
     */
    void updateProductStatus(Long sellerId, Long productId, Integer status);

    /**
     * 删除商品（我的发布 · 删除）。仅商品归属农户可操作。
     *
     * <p><b>不保证真的删掉了行</b> —— 依据 SRS 例外流程，被订单引用的商品不物理删除，
     * 而是置为下架。调用方必须看返回值再决定提示什么。</p>
     *
     * <p>{@code t_cart.product_id} 有外键指向 {@code t_product}，所以未被订单引用时
     * 会连同该商品在所有人购物车里的条目一起清掉再删行；购物车只是「还没下单的意向」，
     * 若它也能拦住删除，任何人把商品丢进购物车就能让农户永远删不掉自己的商品。</p>
     *
     * @param sellerId  当前登录农户（取自登录态）
     * @param productId 商品主键
     * @return {@link #DELETE_RESULT_DELETED} 或 {@link #DELETE_RESULT_OFF_SHELF}
     */
    String deleteProduct(Long sellerId, Long productId);

    /**
     * 分页查询我发布的商品（含待审核 / 未通过 / 已下架），供农户管理页使用。
     *
     * @param auditStatus 审核状态筛选，为空表示查全部
     */
    IPage<ProductVO> pageMyProducts(Long sellerId, long pageNum, long pageSize, String auditStatus);

    /**
     * 查询商品详情：商品全字段 + 库存 + 发布农户公开信息 + 已过审评论首页。
     *
     * <p>只返回审核通过的商品。买家不能通过本接口看到待审核 / 未通过的内容 ——
     * 「通过后才在前台可见」是 REQ-15 的验收口径，不能只在列表接口上守着。</p>
     *
     * @param productId 商品主键
     * @return 商品详情
     */
    ProductDetailVO productDetail(Long productId);

    /**
     * 加入购物车。
     */
    void addToCart(Long buyerId, Long productId, Integer quantity);

    /**
     * 创建订单并生成订单编号。
     *
     * <p>扣减库存与写入订单在同一事务内完成；库存判断由
     * {@code ProductMapper.deductStock} 的 WHERE 条件承担，并发下不会超卖。
     * 库存在这里只做「扣减」，不做「补回」——补回只在取消订单时发生。</p>
     */
    Long createOrder(Long buyerId, OrderCreateDTO dto);

    /**
     * 分页查询我的购物车。
     */
    IPage<CartVO> pageCart(Long buyerId, long pageNum, long pageSize);

    /**
     * 修改购物车条目数量（quantity 为 0 或负数时视为删除该条目）。
     */
    void updateCartQuantity(Long buyerId, Long cartId, Integer quantity);

    /**
     * 删除购物车条目。
     */
    void removeCartItem(Long buyerId, Long cartId);

    /**
     * 分页查询我买到的订单（买家视角），可按订单状态筛选（orderStatus 为空表示查全部）。
     */
    IPage<OrderVO> pageOrders(Long buyerId, long pageNum, long pageSize, String orderStatus);

    /**
     * 分页查询我卖出的订单（农户视角），可按订单状态筛选（orderStatus 为空表示查全部）。
     *
     * <p>农户没有这个列表就无法看到待发货的订单，「发货」也就没有入口。
     * 查询命中 {@code t_order} 的 {@code idx_order_seller_status} 索引。</p>
     *
     * @param sellerId 当前登录农户（取自登录态）
     */
    IPage<OrderVO> pageSoldOrders(Long sellerId, long pageNum, long pageSize, String orderStatus);

    /**
     * 查询订单详情。订单的买家与卖家都可查看，其他用户一律「订单不存在」。
     */
    OrderVO orderDetail(Long userId, Long orderId);

    /**
     * 支付订单（模拟支付通道，不接入真实资金）。
     *
     * <p>状态跃迁：待支付 → 待发货，同时写入 {@code pay_time}。
     * 只有订单买家本人可操作。</p>
     */
    void payOrder(Long buyerId, Long orderId);

    /**
     * 订单发货。只有订单的卖家（农户）可操作。
     *
     * <p>状态跃迁：待发货 → 待收货，同时写入 {@code deliver_time}。</p>
     */
    void deliverOrder(Long sellerId, Long orderId);

    /**
     * 订单收货。只有订单买家本人可操作，确认后订单完成。
     *
     * <p>状态跃迁：待收货 → 已完成，同时写入 {@code receive_time}。</p>
     */
    void receiveOrder(Long buyerId, Long orderId);

    /**
     * 取消订单：仅「待支付/待发货」可取消，取消后回滚库存。
     */
    void cancelOrder(Long buyerId, Long orderId);

}
