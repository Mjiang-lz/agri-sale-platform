package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.entity.Cart;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.mapper.CartMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.service.TradeService;
import com.rxt.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 农产品交易模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    private final OrderMapper orderMapper;

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
        // 1. 校验商品：必须存在、已通过审核、已上架
        Product product = productMapper.selectById(productId);
        if (product == null || !"已通过".equals(product.getAuditStatus()) || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }
        // 2. 校验库存是否足够
        if (product.getStock() < quantity) {
            throw new RuntimeException("库存不足");
        }
        // 3. 计算本条购物车的金额（单价 × 数量，用 BigDecimal 避免精度丢失）
        BigDecimal unitPrice = product.getPrice();
        BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // 4. 看购物车里是否已经有这个买家的同款商品
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getBuyerId, buyerId).eq(Cart::getProductId, productId);
        Cart exist = cartMapper.selectOne(wrapper);
        if (exist != null) {
            // 已存在 → 数量累加，金额按新数量重算
            int newQty = exist.getQuantity() + quantity;
            exist.setQuantity(newQty);
            exist.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(newQty)));
            cartMapper.updateById(exist);
        } else {
            // 不存在 → 新增一条
            Cart cart = new Cart();
            cart.setBuyerId(buyerId);
            cart.setProductId(productId);
            cart.setQuantity(quantity);
            cart.setTotalAmount(lineAmount);
            cartMapper.insert(cart);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long buyerId, OrderCreateDTO dto) {
        // 1. 校验商品：存在、已通过、已上架
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null || !"已通过".equals(product.getAuditStatus()) || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }
        // 2. 校验库存
        if (product.getStock() < dto.getQuantity()) {
            throw new RuntimeException("库存不足");
        }

        // 3. 扣减库存（在同一事务里，报错会自动回滚）
        product.setStock(product.getStock() - dto.getQuantity());
        productMapper.updateById(product);

        // 4. 生成唯一订单编号：RX + 年月日时分秒 + 4 位随机数
        String orderNo = generateOrderNo();

        // 5. 组装订单（unitPrice 做价格快照，避免后续改价影响老订单）
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setOrderAmount(product.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        order.setOrderStatus("待支付");

        // 6. 写入订单
        orderMapper.insert(order);
        return order.getOrderId();
    }

    /**
     * 生成订单编号。
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return "RX" + timestamp + random;
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
