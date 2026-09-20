package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.entity.Cart;
import com.rxt.entity.FarmerProfile;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.entity.User;
import com.rxt.mapper.CartMapper;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.service.CommentService;
import com.rxt.service.TradeService;
import com.rxt.vo.CartVO;
import com.rxt.vo.CommentVO;
import com.rxt.vo.OrderVO;
import com.rxt.vo.ProductDetailVO;
import com.rxt.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 农产品交易模块业务实现。
 *
 * <p>商品检索、购物车、订单三组功能均已实现，业务规则见《软件需求规格说明书》
 * 对应功能点；未实现的部分在接口方法注释里标注。</p>
 */
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    /** 审核通过状态，取值与 t_product.audit_status 一致 */
    private static final String AUDIT_APPROVED = "已通过";

    /** 商品详情内嵌的评论条数。看更多走 GET /api/comment/product/list 翻页 */
    private static final int DETAIL_COMMENT_SIZE = 5;

    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final FarmerProfileMapper farmerProfileMapper;
    private final CommentService commentService;

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
    public ProductDetailVO productDetail(Long productId) {
        // 1. 商品必须存在且已通过审核。
        //    「通过后才在前台可见」是 REQ-15 的验收口径，不能只在列表接口上守 ——
        //    否则拿着 id 直接请求详情就能看到待审 / 被驳回的内容，等于绕过了审核。
        Product product = productMapper.selectById(productId);
        if (product == null || !AUDIT_APPROVED.equals(product.getAuditStatus())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在或尚未上架");
        }

        // 2. 商品自身字段。images 由 JacksonTypeHandler 直接从 JSON 列转成 List<String>，
        //    这里不需要手工解析字符串。
        ProductDetailVO vo = new ProductDetailVO();
        vo.setProductId(product.getProductId());
        vo.setProductName(product.getProductName());
        vo.setCategory(product.getCategory());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setUnit(product.getUnit());
        vo.setImageUrl(product.getImageUrl());
        // 历史商品的 images 列是 NULL，直接返回会让前端拿到 null 再遍历时炸掉。
        // 统一成空数组，前端只需处理「有没有图」，不用再判空。
        vo.setImages(product.getImages() == null ? List.of() : product.getImages());
        vo.setDescription(product.getDescription());
        vo.setOrigin(product.getOrigin());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());

        // 3. 发布农户的公开信息
        fillSellerInfo(vo, product.getSellerId());

        // 4. 评论首页。复用 CommentService 而不是自己写一份查询，
        //    是为了让「只返回已过审评论」这条规则只有一处实现 ——
        //    两处各写一遍，迟早会出现一边改了另一边忘了。
        IPage<CommentVO> commentPage = commentService.pageProductComments(productId, 1, DETAIL_COMMENT_SIZE);
        vo.setCommentTotal(commentPage.getTotal());
        vo.setComments(commentPage.getRecords());

        return vo;
    }

    @Override
    public void addToCart(Long buyerId, Long productId, Integer quantity) {
        // 1. 校验商品：必须存在、已通过审核、已上架
        Product product = productMapper.selectById(productId);
        if (product == null || !"已通过".equals(product.getAuditStatus()) || product.getStatus() != 1) {
            throw new BizException("商品不存在或已下架");
        }
        // 2. 校验库存是否足够
        if (product.getStock() < quantity) {
            throw new BizException("库存不足");
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
            throw new BizException("商品不存在或已下架");
        }
        // 2. 校验库存
        if (product.getStock() < dto.getQuantity()) {
            throw new BizException("库存不足");
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

    @Override
    public IPage<CartVO> pageCart(Long buyerId, long pageNum, long pageSize) {
        // 1. 只查当前买家的购物车条目，按加入时间倒序（最新加的排前面）
        Page<Cart> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getBuyerId, buyerId)
                .orderByDesc(Cart::getCreateTime);

        // 2. 分页查询后把 Cart 转成 CartVO（VO 里会补齐商品名称、单价、库存等）
        Page<Cart> cartPage = cartMapper.selectPage(page, wrapper);
        return cartPage.convert(this::convertToCartVO);
    }

    @Override
    public void updateCartQuantity(Long buyerId, Long cartId, Integer quantity) {
        // 1. 参数校验
        if (quantity == null || quantity < 0) {
            throw new BizException("数量不能为负数");
        }
        // 2. 校验条目归属：只能改自己的购物车
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !Objects.equals(cart.getBuyerId(), buyerId)) {
            throw new BizException("购物车条目不存在");
        }
        // 3. 数量为 0 视为删除该条目
        if (quantity == 0) {
            cartMapper.deleteById(cartId);
            return;
        }
        // 4. 校验商品状态与库存
        Product product = productMapper.selectById(cart.getProductId());
        if (product == null || !"已通过".equals(product.getAuditStatus()) || product.getStatus() != 1) {
            throw new BizException("商品不存在或已下架");
        }
        if (product.getStock() < quantity) {
            throw new BizException("库存不足，当前仅剩 " + product.getStock());
        }
        // 5. 更新数量并按商品当前价重算小计（价格以商品表为准，购物车不锁价）
        cart.setQuantity(quantity);
        cart.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        cartMapper.updateById(cart);
    }

    @Override
    public void removeCartItem(Long buyerId, Long cartId) {
        // 1. 校验归属：只能删自己的购物车条目
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !Objects.equals(cart.getBuyerId(), buyerId)) {
            throw new BizException("购物车条目不存在");
        }
        // 2. 删除
        cartMapper.deleteById(cartId);
    }

    @Override
    public IPage<OrderVO> pageOrders(Long buyerId, long pageNum, long pageSize, String orderStatus) {
        // 1. 只查当前买家的订单，按下单时间倒序（最新的排前面）
        Page<Order> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId);
        // 传了状态就按状态筛，不传查全部
        if (StringUtils.hasText(orderStatus)) {
            wrapper.eq(Order::getOrderStatus, orderStatus);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        // 2. 分页查询后转 VO（VO 里会补齐商品名称、单位、图片）
        Page<Order> orderPage = orderMapper.selectPage(page, wrapper);
        return orderPage.convert(this::convertToOrderVO);
    }

    @Override
    public OrderVO orderDetail(Long buyerId, Long orderId) {
        // 1. 校验归属：只能看自己的订单
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BizException("订单不存在");
        }
        return convertToOrderVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long buyerId, Long orderId) {
        // 1. 校验归属：只能取消自己的订单
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BizException("订单不存在");
        }
        // 2. 校验状态：已发货/已收货/已完成/已取消都不能再取消
        String status = order.getOrderStatus();
        if ("已取消".equals(status)) {
            throw new BizException("订单已取消，请勿重复操作");
        }
        if ("待收货".equals(status) || "已完成".equals(status)) {
            throw new BizException("订单已发货，无法取消，请联系卖家");
        }
        // 3. 回滚库存（下单时扣了多少就还多少，与状态更新在同一事务里）
        Product product = productMapper.selectById(order.getProductId());
        if (product != null) {
            product.setStock(product.getStock() + order.getQuantity());
            productMapper.updateById(product);
        }
        // 4. 更新订单状态
        order.setOrderStatus("已取消");
        orderMapper.updateById(order);
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
     * 购物车实体转视图对象，补齐商品名称、单价、单位、库存等展示信息。
     */
    private CartVO convertToCartVO(Cart cart) {
        CartVO vo = new CartVO();
        vo.setCartId(cart.getCartId());
        vo.setProductId(cart.getProductId());
        vo.setQuantity(cart.getQuantity());
        vo.setTotalAmount(cart.getTotalAmount());
        vo.setCreateTime(cart.getCreateTime());

        // 补齐商品信息（商品可能已被删除/下架，这里允许 product 为 null，前端按置灰处理）
        Product product = productMapper.selectById(cart.getProductId());
        if (product != null) {
            vo.setProductName(product.getProductName());
            vo.setImageUrl(product.getImageUrl());
            vo.setUnit(product.getUnit());
            vo.setPrice(product.getPrice());
            vo.setStock(product.getStock());
            vo.setStatus(product.getStatus());
        }
        return vo;
    }

    /**
     * 订单实体转视图对象，补齐商品名称、单位、图片等展示信息。
     */
    private OrderVO convertToOrderVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getOrderId());
        vo.setOrderNo(order.getOrderNo());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setProductId(order.getProductId());
        vo.setQuantity(order.getQuantity());
        vo.setUnitPrice(order.getUnitPrice());
        vo.setOrderAmount(order.getOrderAmount());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setPayTime(order.getPayTime());
        vo.setDeliverTime(order.getDeliverTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setCreateTime(order.getCreateTime());

        // 补齐商品信息（商品可能已被删除/下架，这里允许 product 为 null）
        Product product = productMapper.selectById(order.getProductId());
        if (product != null) {
            vo.setProductName(product.getProductName());
            vo.setImageUrl(product.getImageUrl());
            vo.setUnit(product.getUnit());
        }
        return vo;
    }

    /**
     * 商品实体转视图对象。
     */
    /**
     * 补齐商品详情里的农户公开信息。
     *
     * <p>只取姓名、头像，以及农户档案里的主营农产品与经营规模。
     * <b>刻意不取 {@code credit_record}</b> —— 那份文本里含「意向额度 10 万元」
     * 这类融资意向，属于审核员与融资模块的信息，不该出现在买家看得到的商品详情里。</p>
     *
     * <p>两次查询都只 select 需要的列，既避免把 {@code phone} / {@code password}
     * 带进内存，也省掉无用的网络传输。</p>
     */
    private void fillSellerInfo(ProductDetailVO vo, Long sellerId) {
        vo.setSellerId(sellerId);
        if (sellerId == null) {
            return;
        }

        User seller = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getUserId, User::getRealName, User::getAvatarUrl)
                .eq(User::getUserId, sellerId));
        if (seller != null) {
            vo.setSellerName(seller.getRealName());
            vo.setSellerAvatarUrl(seller.getAvatarUrl());
        }

        // 农户档案是一对一（uk_farmer_profile_user_id），所以取一条即可
        FarmerProfile profile = farmerProfileMapper.selectOne(new LambdaQueryWrapper<FarmerProfile>()
                .select(FarmerProfile::getMainProduct, FarmerProfile::getScaleDesc)
                .eq(FarmerProfile::getUserId, sellerId));
        if (profile != null) {
            vo.setSellerMainProduct(profile.getMainProduct());
            vo.setSellerScaleDesc(profile.getScaleDesc());
        }
    }

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
