package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.security.UserContext;
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
     */
    @PostMapping("/cart")
    public Result<Void> addToCart(@RequestParam Long productId, @RequestParam Integer quantity) {
        tradeService.addToCart(UserContext.currentUserId(), productId, quantity);
        return Result.success();
    }

    /**
     * 创建订单。
     */
    @PostMapping("/order")
    public Result<Long> createOrder(@RequestBody @Valid OrderCreateDTO dto) {
        return Result.success(tradeService.createOrder(UserContext.currentUserId(), dto));
    }
}
