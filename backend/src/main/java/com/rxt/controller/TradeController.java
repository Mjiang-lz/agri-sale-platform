package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.dto.ProductPublishDTO;
import com.rxt.security.UserContext;
import com.rxt.service.TradeService;
import com.rxt.vo.CartVO;
import com.rxt.vo.CategoryStatVO;
import com.rxt.vo.OrderVO;
import com.rxt.vo.ProductDetailVO;
import com.rxt.vo.ProductVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 农产品交易接口。基础路径 /api/trade。
 *
 * <p>购物车与订单属于「我的数据」，身份一律从 {@link UserContext} 取当前登录用户。
 * 不再接受客户端传入 buyerId —— 用请求参数传递身份可以被随意伪造，
 * 任何登录用户都能拿别人的 id 操作他人购物车/订单（越权）。</p>
 */
@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * 分页检索商品货源。公开接口，游客可访问。
     *
     * <p>四个筛选条件（关键词 / 品类 / 价格区间 / 产地）可任意组合，都不传即返回在架全部商品。</p>
     */
    @GetMapping("/products")
    public Result<IPage<ProductVO>> pageProducts(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) BigDecimal minPrice,
                                                 @RequestParam(required = false) BigDecimal maxPrice,
                                                 @RequestParam(required = false) String origin) {
        return Result.success(tradeService.pageProducts(pageNum, pageSize, keyword,
                category, minPrice, maxPrice, origin));
    }

    /**
     * 查询全部在架商品涉及的品类及各自数量。公开接口，游客可访问。
     *
     * <p><b>路径为什么是 {@code /categories} 而不是 {@code /products/categories}</b>：
     * {@code WebMvcConfig} 的放行清单里写了 {@code /trade/products}，
     * 而 {@code excludePathPatterns} <b>只按路径匹配</b>，前缀匹配规则下
     * {@code /trade/products/categories} 也落在同一条放行规则内 ——
     * 换句话说两种写法都能公开，但独立一级路径更清楚，
     * 且不会让人误以为它隶属于「单个商品」的子资源。</p>
     */
    @GetMapping("/categories")
    public Result<List<CategoryStatVO>> listCategories() {
        return Result.success(tradeService.listCategories());
    }

    /**
     * 发布商品。仅农户可操作，新商品进入待审核队列且默认下架。
     *
     * <p>路径刻意用 {@code /product/publish} 而不是 {@code POST /products}：
     * {@code WebMvcConfig} 的放行清单里写了 {@code /trade/products}（公开的货源列表），
     * 而 Spring 的 {@code excludePathPatterns} <b>只按路径匹配、不区分 HTTP 方法</b>，
     * 被排除的路径连拦截器的 preHandle 都不会执行 ——
     * 与列表同路径的 POST 请求因此拿不到登录态（UserContext 恒为空）。
     * 改用子路径后走正常拦截器链，登录与角色校验才成立。
     * 命名与 M3 的 {@code POST /demand/publish} 保持一致。</p>
     */
    @PostMapping("/product/publish")
    public Result<Long> publishProduct(@RequestBody @Valid ProductPublishDTO dto) {
        return Result.success(tradeService.publishProduct(UserContext.currentUserId(), dto));
    }

    /**
     * 编辑商品。只能编辑自己发布的商品（归属校验在 service 层）。
     */
    @PutMapping("/products/{productId}")
    public Result<Void> updateProduct(@PathVariable Long productId,
                                      @RequestBody @Valid ProductPublishDTO dto) {
        tradeService.updateProduct(UserContext.currentUserId(), productId, dto);
        return Result.success();
    }

    /**
     * 商品上下架。status：1 上架，0 下架。未通过审核的商品不允许上架。
     */
    @PutMapping("/products/{productId}/status")
    public Result<Void> updateProductStatus(@PathVariable Long productId,
                                            @RequestParam Integer status) {
        tradeService.updateProductStatus(UserContext.currentUserId(), productId, status);
        return Result.success();
    }

    /**
     * 删除商品（我的发布 · 删除）。仅商品归属农户可操作。
     *
     * <p><b>返回的 {@code data} 不是可有可无的附属信息</b>：本接口不保证真的删掉了行。
     * 依据 SRS 例外流程，被订单引用的商品会改判为下架并保持数据完整，
     * 所以必须把「删掉了」与「只下架了」区分开：
     * {@code DELETED} = 整行已移除；{@code OFF_SHELF} = 被订单引用，已改为下架。</p>
     *
     * <p>前端要读这个值而不是读文案 —— {@code request.js} 只透出 {@code Result.data}，
     * {@code message} 在成功分支里根本不会到达页面。</p>
     */
    @DeleteMapping("/products/{productId}")
    public Result<String> deleteProduct(@PathVariable Long productId) {
        String outcome = tradeService.deleteProduct(UserContext.currentUserId(), productId);
        return TradeService.DELETE_RESULT_OFF_SHELF.equals(outcome)
                ? Result.success("该商品已被订单引用，不能删除，已改为下架", outcome)
                : Result.success("商品已删除", outcome);
    }

    /**
     * 我发布的商品（含待审核 / 未通过 / 已下架），农户管理页用。
     */
    @GetMapping("/my-products")
    public Result<IPage<ProductVO>> pageMyProducts(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String auditStatus) {
        return Result.success(tradeService.pageMyProducts(UserContext.currentUserId(),
                pageNum, pageSize, auditStatus));
    }

    /**
     * 商品详情：商品全字段 + 库存 + 发布农户公开信息 + 已过审评论首页。
     *
     * <p>路径用 {@code /products/{productId}} 与列表接口 {@code /products} 成对，
     * Spring 能区分「精确匹配的 /products」与「带路径变量的 /products/{id}」。</p>
     *
     * <p>只返回审核通过的商品；待审 / 未通过的商品在这里同样不可见（404）。</p>
     */
    @GetMapping("/products/{productId}")
    public Result<ProductDetailVO> productDetail(@PathVariable Long productId) {
        return Result.success(tradeService.productDetail(productId));
    }

    /**
     * 加入购物车。买家身份取自登录态。
     */
    @PostMapping("/cart")
    public Result<Void> addToCart(@RequestParam Long productId,
                                 @RequestParam Integer quantity) {
        tradeService.addToCart(UserContext.currentUserId(), productId, quantity);
        return Result.success();
    }

    /**
     * 创建订单。买家身份取自登录态。
     */
    @PostMapping("/order")
    public Result<Long> createOrder(@RequestBody @Valid OrderCreateDTO dto) {
        return Result.success(tradeService.createOrder(UserContext.currentUserId(), dto));
    }

    /**
     * 查询我的购物车（分页）。
     */
    @GetMapping("/cart/list")
    public Result<IPage<CartVO>> pageCart(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(tradeService.pageCart(UserContext.currentUserId(), pageNum, pageSize));
    }

    /**
     * 修改购物车条目数量。quantity 传 0 表示删除该条目。
     * 只能操作自己的条目，归属校验在 service 层完成。
     */
    @PutMapping("/cart/{cartId}")
    public Result<Void> updateCartQuantity(@PathVariable Long cartId,
                                           @RequestParam Integer quantity) {
        tradeService.updateCartQuantity(UserContext.currentUserId(), cartId, quantity);
        return Result.success();
    }

    /**
     * 删除购物车条目。只能删自己的条目。
     */
    @DeleteMapping("/cart/{cartId}")
    public Result<Void> removeCartItem(@PathVariable Long cartId) {
        tradeService.removeCartItem(UserContext.currentUserId(), cartId);
        return Result.success();
    }

    /**
     * 查询我的订单（分页），可按订单状态筛选。
     */
    @GetMapping("/order/list")
    public Result<IPage<OrderVO>> pageOrders(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String orderStatus) {
        return Result.success(tradeService.pageOrders(UserContext.currentUserId(), pageNum, pageSize, orderStatus));
    }

    /**
     * 查询我卖出的订单（农户视角，分页），可按订单状态筛选。发货的入口。
     *
     * <p>路径 {@code /order/sold} 是字面量段，与下面的 {@code /order/{orderId}} 不冲突 ——
     * Spring 的 PathPattern 按「字面量优先于路径变量」排序匹配，
     * {@code /order/sold} 不会被 {@code /order/{orderId}} 抢走。</p>
     */
    @GetMapping("/order/sold")
    public Result<IPage<OrderVO>> pageSoldOrders(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String orderStatus) {
        return Result.success(tradeService.pageSoldOrders(UserContext.currentUserId(), pageNum, pageSize, orderStatus));
    }

    /**
     * 查询订单详情。订单的买家与卖家都能查看，其他用户返回「订单不存在」。
     */
    @GetMapping("/order/{orderId}")
    public Result<OrderVO> orderDetail(@PathVariable Long orderId) {
        return Result.success(tradeService.orderDetail(UserContext.currentUserId(), orderId));
    }

    /**
     * 支付订单（模拟支付通道，不接入真实资金）。
     *
     * <p>状态流转：待支付 → 待发货，写入 pay_time。仅订单买家本人可操作。
     * 无请求体 —— 「支付」这个动作本身就是全部语义，目标状态由当前状态推得。</p>
     */
    @PostMapping("/order/{orderId}/pay")
    public Result<Void> payOrder(@PathVariable Long orderId) {
        tradeService.payOrder(UserContext.currentUserId(), orderId);
        return Result.success();
    }

    /**
     * 订单发货。状态流转：待发货 → 待收货，写入 deliver_time。仅订单的农户可操作。
     */
    @PostMapping("/order/{orderId}/deliver")
    public Result<Void> deliverOrder(@PathVariable Long orderId) {
        tradeService.deliverOrder(UserContext.currentUserId(), orderId);
        return Result.success();
    }

    /**
     * 确认收货。状态流转：待收货 → 已完成，写入 receive_time。仅订单买家本人可操作。
     */
    @PostMapping("/order/{orderId}/receive")
    public Result<Void> receiveOrder(@PathVariable Long orderId) {
        tradeService.receiveOrder(UserContext.currentUserId(), orderId);
        return Result.success();
    }

    /**
     * 取消订单：仅「待支付/待发货」可取消，取消后回滚库存。
     */
    @PostMapping("/order/{orderId}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long orderId) {
        tradeService.cancelOrder(UserContext.currentUserId(), orderId);
        return Result.success();
    }
}
