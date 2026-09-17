package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.service.TradeService;
import com.rxt.vo.ProductVO;
import org.springframework.stereotype.Service;

/**
 * 农产品交易模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class TradeServiceImpl implements TradeService {

    @Override
    public IPage<ProductVO> pageProducts(long pageNum, long pageSize, String keyword) {
        // TODO 待实现：用 Page<Product> 查询后转换为 Page<ProductVO>
        return null;
    }

    @Override
    public void addToCart(Long buyerId, Long productId, Integer quantity) {
        // TODO 待实现：校验商品上架状态与库存，同商品重复加购则累加数量
    }

    @Override
    public Long createOrder(Long buyerId, OrderCreateDTO dto) {
        // TODO 待实现：价格快照、库存扣减、生成订单编号，需在事务中完成
        return null;
    }
}
