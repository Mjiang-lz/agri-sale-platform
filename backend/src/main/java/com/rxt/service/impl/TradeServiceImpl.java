package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.entity.Product;
import com.rxt.mapper.ProductMapper;
import com.rxt.service.TradeService;
import com.rxt.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 农产品交易模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final ProductMapper productMapper;

    @Override
    public IPage<ProductVO> pageProducts(long pageNum, long pageSize, String keyword) {
        // 1. 构造分页对象
        Page<Product> page = new Page<>(pageNum, pageSize);

        // 2. 构造查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 只展示"已通过"且"上架"的商品
        wrapper.eq(Product::getAuditStatus, "已通过")
                .eq(Product::getStatus, 1);

        // keyword 非空时按商品名称模糊匹配
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getProductName, keyword);
        }

        // 3. 按创建时间倒序，最新的排前面
        wrapper.orderByDesc(Product::getCreateTime);

        // 4. 执行分页查询
        Page<Product> productPage = productMapper.selectPage(page, wrapper);

        // 5. 把 Product 转成 ProductVO
        return productPage.convert(this::convertToProductVO);
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
    /**
     * 商品实体转视图对象。
     */
    private ProductVO convertToProductVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setProductId(product.getProductId());
        vo.setProductName(product.getProductName());
        vo.setCategory(product.getCategory());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setUnit(product.getUnit());
        vo.setImageUrl(product.getImageUrl());
        vo.setSellerId(product.getSellerId());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setStatus(product.getStatus());
        return vo;
    }
}
