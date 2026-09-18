package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.service.TradeService;
import com.rxt.vo.ProductVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
