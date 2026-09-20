package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.security.UserContext;
import com.rxt.service.TradeService;
import com.rxt.vo.CartVO;
import com.rxt.vo.OrderVO;
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
     */
    @GetMapping("/products")
    public Result<IPage<ProductVO>> pageProducts(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword) {
        return Result.success(tradeService.pageProducts(pageNum, pageSize, keyword));
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
     * 查询订单详情，只能查自己的订单。
     */
    @GetMapping("/order/{orderId}")
    public Result<OrderVO> orderDetail(@PathVariable Long orderId) {
        return Result.success(tradeService.orderDetail(UserContext.currentUserId(), orderId));
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
