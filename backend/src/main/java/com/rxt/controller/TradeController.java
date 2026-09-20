package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.OrderCreateDTO;
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
 */
@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * 分页检索商品货源。
     */
    @GetMapping("/products")
    public Result<IPage<ProductVO>> pageProducts(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword) {
        return Result.success(tradeService.pageProducts(pageNum, pageSize, keyword));
    }

    /**
     * 加入购物车。
     * 注：登录鉴权(AuthServiceImpl)未实现，临时用 buyerId 参数传入；
     * 鉴权完成后改回 tradeService.addToCart(UserContext.currentUserId(), productId, quantity)。
     */
    @PostMapping("/cart")
    public Result<Void> addToCart(@RequestParam Long buyerId,
                                 @RequestParam Long productId,
                                 @RequestParam Integer quantity) {
        tradeService.addToCart(buyerId, productId, quantity);
        return Result.success();
    }

    /**
     * 创建订单。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入；鉴权完成后改回 UserContext.currentUserId()。
     */
    @PostMapping("/order")
    public Result<Long> createOrder(@RequestParam Long buyerId,
                                   @RequestBody @Valid OrderCreateDTO dto) {
        return Result.success(tradeService.createOrder(buyerId, dto));
    }

    /**
     * 查询我的购物车（分页）。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入；鉴权完成后改回 UserContext.currentUserId()。
     */
    @GetMapping("/cart/list")
    public Result<IPage<CartVO>> pageCart(@RequestParam Long buyerId,
                                          @RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(tradeService.pageCart(buyerId, pageNum, pageSize));
    }

    /**
     * 修改购物车条目数量。quantity 传 0 表示删除该条目。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入。
     */
    @PutMapping("/cart/{cartId}")
    public Result<Void> updateCartQuantity(@PathVariable Long cartId,
                                           @RequestParam Long buyerId,
                                           @RequestParam Integer quantity) {
        tradeService.updateCartQuantity(buyerId, cartId, quantity);
        return Result.success();
    }

    /**
     * 删除购物车条目。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入。
     */
    @DeleteMapping("/cart/{cartId}")
    public Result<Void> removeCartItem(@PathVariable Long cartId,
                                       @RequestParam Long buyerId) {
        tradeService.removeCartItem(buyerId, cartId);
        return Result.success();
    }

    /**
     * 查询我的订单（分页），可按订单状态筛选。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入。
     */
    @GetMapping("/order/list")
    public Result<IPage<OrderVO>> pageOrders(@RequestParam Long buyerId,
                                             @RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String orderStatus) {
        return Result.success(tradeService.pageOrders(buyerId, pageNum, pageSize, orderStatus));
    }

    /**
     * 查询订单详情，只能查自己的订单。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入。
     */
    @GetMapping("/order/{orderId}")
    public Result<OrderVO> orderDetail(@PathVariable Long orderId,
                                       @RequestParam Long buyerId) {
        return Result.success(tradeService.orderDetail(buyerId, orderId));
    }

    /**
     * 取消订单：仅"待支付/待发货"可取消，取消后回滚库存。
     * 注：登录鉴权未实现，临时用 buyerId 参数传入。
     */
    @PostMapping("/order/{orderId}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long orderId,
                                    @RequestParam Long buyerId) {
        tradeService.cancelOrder(buyerId, orderId);
        return Result.success();
    }
}
