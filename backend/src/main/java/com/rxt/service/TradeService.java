package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.vo.ProductVO;

/**
 * 农产品交易模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface TradeService {

    /**
     * 分页检索商品货源。
     */
    IPage<ProductVO> pageProducts(long pageNum, long pageSize, String keyword);

    /**
     * 加入购物车。
     */
    void addToCart(Long buyerId, Long productId, Integer quantity);

    /**
     * 创建订单并生成订单编号。
     */
    Long createOrder(Long buyerId, OrderCreateDTO dto);

}
