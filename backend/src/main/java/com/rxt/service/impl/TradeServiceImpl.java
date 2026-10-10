package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.dto.ProductPublishDTO;
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
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.AddressService;
import com.rxt.service.CommentService;
import com.rxt.service.TradeService;
import com.rxt.vo.AddressVO;
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

    /** 待审核状态。新发布 / 内容变更后的商品都进这个队列 */
    private static final String AUDIT_PENDING = "待审核";

    /** 上架 */
    private static final int STATUS_ON = 1;

    /** 下架 */
    private static final int STATUS_OFF = 0;

    /** 商品详情内嵌的评论条数。看更多走 GET /api/comment/product/list 翻页 */
    private static final int DETAIL_COMMENT_SIZE = 5;

    // ---- 订单状态常量 ----
    // 取值与 t_order.order_status 的列注释、data.sql 演示数据完全一致。
    // 状态机：待支付 --pay--> 待发货 --deliver--> 待收货 --receive--> 已完成
    //         待支付 / 待发货 可 --cancel--> 已取消（同时回滚库存）

    /** 待支付（下单后的初始状态） */
    private static final String ORDER_PENDING_PAY = "待支付";

    /** 待发货（已付款，等待农户发货） */
    private static final String ORDER_WAIT_DELIVER = "待发货";

    /** 待收货（农户已发货，等待买家确认收货） */
    private static final String ORDER_WAIT_RECEIVE = "待收货";

    /** 已完成（买家已确认收货） */
    private static final String ORDER_FINISHED = "已完成";

    /** 已取消 */
    private static final String ORDER_CANCELED = "已取消";

    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final FarmerProfileMapper farmerProfileMapper;
    private final CommentService commentService;
    private final AddressService addressService;
    private final PermissionChecker permissionChecker;

    @Override
    public IPage<ProductVO> pageProducts(long pageNum, long pageSize, String keyword,
                                         String category, BigDecimal minPrice, BigDecimal maxPrice,
                                         String origin) {
        // 价格区间先做自洽校验：上下限传反了说明前端参数拼错，
        // 直接报错比返回一个空列表更容易定位
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "价格区间不合法：最低价不能高于最高价");
        }

        Page<Product> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 只展示「已通过」且「上架」的商品。这两个条件放最前面，
        // 能命中 idx_product_audit_status 的最左前缀
        wrapper.eq(Product::getAuditStatus, AUDIT_APPROVED)
                .eq(Product::getStatus, STATUS_ON);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getProductName, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Product::getCategory, category);
        }
        // 产地用 like 而不是等值：库里存的是「江西赣州」，
        // 前端既可能按省筛也可能按市筛，等值匹配会一个都命中不了
        if (StringUtils.hasText(origin)) {
            wrapper.like(Product::getOrigin, origin);
        }
        if (minPrice != null) {
            wrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Product::getPrice, maxPrice);
        }

        wrapper.orderByDesc(Product::getCreateTime);

        return productMapper.selectPage(page, wrapper).convert(this::convertToProductVO);
    }

    /**
     * 发布商品。
     *
     * <p>权限校验放在这里而不是拦截器：WebMvcConfig 的白名单是按路径放行的
     * （{@code /trade/products} 为公开的商品列表），路径相同、方法不同的
     * POST 也会被一起放行，所以写操作必须在业务层把身份卡死。</p>
     */
    @Override
    public Long publishProduct(Long sellerId, ProductPublishDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        Product product = new Product();
        applyEditableFields(product, dto);
        product.setSellerId(sellerId);

        // 新发布的商品一律先进待审队列，且默认下架。
        // 审核通过时由审核接口置 status=1（「通过即上架」），农户不必再手动上一次架
        product.setAuditStatus(AUDIT_PENDING);
        product.setStatus(STATUS_OFF);

        productMapper.insert(product);
        return product.getProductId();
    }

    @Override
    public void updateProduct(Long sellerId, Long productId, ProductPublishDTO dto) {
        // 与发布一致：本接口农户专用。归属校验虽然也能挡住非农户（必然 404），
        // 但报「商品不存在」会让人误以为数据丢了，不如直接给「没有该操作的权限」
        permissionChecker.requireRole(RoleConstant.FARMER);

        Product exist = requireOwnProduct(sellerId, productId);

        Product update = new Product();
        update.setProductId(productId);
        applyEditableFields(update, dto);

        // 内容变更需要重新送审。判断依据是「改了什么」而不是「调了哪个接口」：
        // 农户补库存、调价格不该被下架，但改了名称或描述就必须让管理员再看一眼
        if (isContentChanged(exist, dto)) {
            update.setAuditStatus(AUDIT_PENDING);
            update.setStatus(STATUS_OFF);
        }

        productMapper.updateById(update);
    }

    @Override
    public void updateProductStatus(Long sellerId, Long productId, Integer status) {
        // 同 updateProduct：农户专用，先卡角色再校验参数
        permissionChecker.requireRole(RoleConstant.FARMER);

        if (status == null || (status != STATUS_ON && status != STATUS_OFF)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "上下架状态只能是 1（上架）或 0（下架）");
        }

        Product exist = requireOwnProduct(sellerId, productId);

        // 只有审核通过的商品才允许上架，否则拿一个未过审的 id 直接上架就绕开了审核
        if (status == STATUS_ON && !AUDIT_APPROVED.equals(exist.getAuditStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "商品当前审核状态为「" + exist.getAuditStatus() + "」，通过审核后才能上架");
        }

        Product update = new Product();
        update.setProductId(productId);
        update.setStatus(status);
        productMapper.updateById(update);
    }

    @Override
    public IPage<ProductVO> pageMyProducts(Long sellerId, long pageNum, long pageSize, String auditStatus) {
        // 农户管理页专用。不卡角色的话采购方会拿到空列表，看起来像「商品丢了」
        permissionChecker.requireRole(RoleConstant.FARMER);

        Page<Product> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSellerId, sellerId);
        // 农户管理页需要看到待审核和未通过的商品，这里不能像前台列表那样固定过滤「已通过」
        if (StringUtils.hasText(auditStatus)) {
            wrapper.eq(Product::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(Product::getCreateTime);

        // 用 convertToMyProductVO 而不是 convertToProductVO：前者多带 description 与 images，
        // 编辑表单要用它们回显。前台列表用后者，避免一页 12 条各带一段 500 字描述
        return productMapper.selectPage(page, wrapper).convert(this::convertToMyProductVO);
    }

    /**
     * 取商品并校验归属：不存在 404、非本人 403。
     */
    private Product requireOwnProduct(Long sellerId, Long productId) {
        Product exist = productMapper.selectById(productId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在：" + productId);
        }
        // 用 Objects.equals 而不是 !=：两边都可能是 null，直接比较会踩拆箱 NPE
        if (!Objects.equals(exist.getSellerId(), sellerId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "只能操作自己发布的商品");
        }
        return exist;
    }

    /**
     * 把 DTO 里可编辑的字段搬到实体上。
     *
     * <p>只拷贝「发布/编辑表单能填」的字段，seller_id、audit_status、status
     * 由业务流程决定，绝不能让入参覆盖。</p>
     */
    private void applyEditableFields(Product product, ProductPublishDTO dto) {
        product.setProductName(dto.getProductName());
        product.setCategory(dto.getCategory());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setUnit(dto.getUnit());
        product.setDescription(dto.getDescription());
        product.setOrigin(dto.getOrigin());
        product.setImageUrl(dto.getImageUrl());
        product.setImages(dto.getImages());
    }

    /**
     * 内容类字段是否发生变更。价格、库存、单位属于经营信息，不算内容。
     *
     * <p>入参为 null 表示「本次不修改该字段」，不算变更 —— 口径必须与
     * {@code productMapper.updateById}（MP 默认 NOT_NULL 策略，null 字段跳过不写库）一致。
     * 否则前端只提交改动字段时会被判成「内容变更」，商品被无理由下架重审，但字段其实没改。</p>
     */
    private boolean isContentChanged(Product exist, ProductPublishDTO dto) {
        return differs(dto.getProductName(), exist.getProductName())
                || differs(dto.getCategory(), exist.getCategory())
                || differs(dto.getDescription(), exist.getDescription())
                || differs(dto.getOrigin(), exist.getOrigin())
                || differs(dto.getImageUrl(), exist.getImageUrl())
                || differs(dto.getImages(), exist.getImages());
    }

    /**
     * 判断某个字段是否真的被本次请求修改了。
     *
     * @param incoming 入参值，{@code null} 表示本次不修改该字段
     * @param existing 库中现值
     */
    private boolean differs(Object incoming, Object existing) {
        return incoming != null && !Objects.equals(incoming, existing);
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
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商品不存在或已下架");
        }
        // 2. 校验库存是否足够
        if (product.getStock() < quantity) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "库存不足");
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
        // 1. 参数兜底校验。@Min(1) 已在 Controller 层拦过一次，这里再挡一道：
        //    Service 是模块对内的入口，将来别处直接调用时不至于绕过校验。
        //    负数在这里是致命的 —— 扣库存的 SQL 形如 stock = stock - quantity，
        //    传 -5 会让库存不减反增
        if (dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "购买数量至少为 1");
        }

        // 2. 校验收货地址：必须存在且属于当前买家。
        //    loadOwned 内部已做归属校验，传别人的 addressId 会在这里被挡下
        //    （否则可以把货发到任意地址上）。
        //    放在扣库存之前：这是「调用方输入」层面的校验，一次单行读就能完成，
        //    没必要先锁住商品行再报错。
        AddressVO address = addressService.loadOwned(buyerId, dto.getAddressId());

        // 3. 校验商品：存在、已通过、已上架
        //    这两条与下面的库存不足都显式用 400：它们都是「调用方可以自行修正」的问题
        //    （换商品、少买点），若用 BizException(String) 会落到 BIZ_ERROR(500)，
        //    和真正的服务端故障混成同一类，误导日志排查与前端提示
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null || !AUDIT_APPROVED.equals(product.getAuditStatus())
                || product.getStatus() != STATUS_ON) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商品不存在或已下架");
        }

        // 4. 原子扣减库存：判断「够不够」与扣减在同一条 UPDATE 里完成。
        //    ⚠️ 不能改成「先读 stock 判断、再 updateById 回写」——那是读-改-写，
        //    两个并发下单会读到同一个旧库存而双双通过，最终卖出的比库存还多（超卖）。
        //    rows == 0 即表示库存不足（或商品刚被删），两者都按「库存不足」处理。
        //    注意此处 product 对象里的 stock 是读取时的旧值，不可再用于写库。
        int deducted = productMapper.deductStock(dto.getProductId(), dto.getQuantity());
        if (deducted == 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "库存不足");
        }

        // 5. 生成唯一订单编号：RX + 年月日时分秒 + 4 位随机数
        String orderNo = generateOrderNo();

        // 6. 组装订单
        //    unitPrice 做价格快照，避免后续改价影响老订单；
        //    收货人 / 电话 / 地址同样做**下单时快照**（不是存 addressId），
        //    理由见 Order 实体上 consignee 字段的注释 —— 地址改了删了都不该
        //    影响历史订单里记的「当时发到哪」。
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setOrderAmount(product.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        order.setConsignee(address.getConsignee());
        order.setConsigneePhone(address.getPhone());
        order.setReceiverAddress(address.getFullAddress());
        order.setOrderRemark(StringUtils.hasText(dto.getOrderRemark()) ? dto.getOrderRemark().trim() : null);
        order.setOrderStatus(ORDER_PENDING_PAY);

        // 7. 写入订单
        orderMapper.insert(order);

        // 8. 把这一条从购物车里清掉。
        //    放在 createOrder 里而不是让前端下单成功后再调一次删除接口，有两个原因：
        //      ① 事务性 —— 下单与清车在同一次事务里。前端分两步做的话，
        //         用户在网络慢时关掉页面，订单已生成、购物车里还留着同一件货，
        //         下次进来会再买一遍；
        //      ② 「立即购买」（不走购物车）这条路径同样安全 ——
        //         t_cart 对 (buyer_id, product_id) 有唯一键 uk_cart_buyer_product，
        //         删除命中 0 行不会报错，也不需要先判断存不存在。
        //    只在订单真正落库后执行，所以下单失败（库存不足等）时购物车原样保留。
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<>();
        cartWrapper.eq(Cart::getBuyerId, buyerId).eq(Cart::getProductId, dto.getProductId());
        cartMapper.delete(cartWrapper);

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
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "数量不能为负数");
        }
        // 2. 校验条目归属：只能改自己的购物车
        //    「不存在」与「不是你的」合并为 404、不区分 —— 区分开就等于提供了一个
        //    按 cartId 探测他人购物车是否存在（以及是否存在该条目）的接口。
        //    与订单侧「非买家也非卖家一律 404」是同一条原则。
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !Objects.equals(cart.getBuyerId(), buyerId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "购物车条目不存在");
        }
        // 3. 数量为 0 视为删除该条目
        if (quantity == 0) {
            cartMapper.deleteById(cartId);
            return;
        }
        // 4. 校验商品状态与库存
        Product product = productMapper.selectById(cart.getProductId());
        if (product == null || !"已通过".equals(product.getAuditStatus()) || product.getStatus() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商品不存在或已下架");
        }
        if (product.getStock() < quantity) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "库存不足，当前仅剩 " + product.getStock());
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
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "购物车条目不存在");
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
    public IPage<OrderVO> pageSoldOrders(Long sellerId, long pageNum, long pageSize, String orderStatus) {
        // 农户视角的「我卖出的」。没有这个列表就找不到待发货的订单，发货也就没有入口。
        // 查询条件 (seller_id, order_status) 正好命中 idx_order_seller_status
        Page<Order> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getSellerId, sellerId);
        if (StringUtils.hasText(orderStatus)) {
            wrapper.eq(Order::getOrderStatus, orderStatus);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> soldPage = orderMapper.selectPage(page, wrapper);
        return soldPage.convert(this::convertToOrderVO);
    }

    @Override
    public OrderVO orderDetail(Long userId, Long orderId) {
        // 订单的买卖双方都能看详情：农户要看到订单内容才好安排发货，
        // 只有「我卖出的」列表不足以支撑发货页。
        // 非参与方一律报「订单不存在」，不泄露订单是否存在
        Order order = orderMapper.selectById(orderId);
        if (order == null || !isParticipant(order, userId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return convertToOrderVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long buyerId, Long orderId) {
        Order order = requireBuyerOrder(buyerId, orderId);

        // 先给友好文案（绝大多数失败是「状态本来就不对」，不是并发），
        // 下面再用带条件的原子更新兜住并发重复提交
        if (!ORDER_PENDING_PAY.equals(order.getOrderStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "当前订单状态为「" + order.getOrderStatus() + "」，只有「待支付」的订单可以支付");
        }

        // 模拟支付通道：按说明书「第三方支付为模拟通道，不接入真实资金」，
        // 这里不做任何资金动作，只把状态推进到「待发货」并记下支付时间。
        // WHERE 里带 order_status='待支付'：重复提交时后一次影响 0 行
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .eq(Order::getOrderStatus, ORDER_PENDING_PAY)
                .set(Order::getOrderStatus, ORDER_WAIT_DELIVER)
                .setSql("pay_time = NOW()"));
        requireStatusChanged(rows, "支付");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverOrder(Long sellerId, Long orderId) {
        // 发货是农户专属动作，显式声明角色意图（归属校验也会挡住，但报错更准确）
        permissionChecker.requireRole(RoleConstant.FARMER);

        Order order = requireSellerOrder(sellerId, orderId);

        if (!ORDER_WAIT_DELIVER.equals(order.getOrderStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "当前订单状态为「" + order.getOrderStatus() + "」，只有「待发货」的订单可以发货");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .eq(Order::getOrderStatus, ORDER_WAIT_DELIVER)
                .set(Order::getOrderStatus, ORDER_WAIT_RECEIVE)
                .setSql("deliver_time = NOW()"));
        requireStatusChanged(rows, "发货");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(Long buyerId, Long orderId) {
        Order order = requireBuyerOrder(buyerId, orderId);

        if (!ORDER_WAIT_RECEIVE.equals(order.getOrderStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "当前订单状态为「" + order.getOrderStatus() + "」，只有「待收货」的订单可以确认收货");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .eq(Order::getOrderStatus, ORDER_WAIT_RECEIVE)
                .set(Order::getOrderStatus, ORDER_FINISHED)
                .setSql("receive_time = NOW()"));
        requireStatusChanged(rows, "收货");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long buyerId, Long orderId) {
        // 1. 校验归属：只能取消自己的订单
        Order order = requireBuyerOrder(buyerId, orderId);

        // 2. 状态提示：区分「重复取消」与「已发货不能取消」，让前端能给出准确文案
        String status = order.getOrderStatus();
        if (ORDER_CANCELED.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单已取消，请勿重复操作");
        }
        if (!ORDER_PENDING_PAY.equals(status) && !ORDER_WAIT_DELIVER.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单已发货，无法取消，请联系卖家");
        }

        // 3. 原子更新状态：把两个可取消状态写进 WHERE。
        //    只有上面那段 if 是挡不住并发的 —— 两个请求同时读到「待支付」会双双通过，
        //    随后库存被回滚两次。rows == 0 说明状态刚被别的请求改掉
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .in(Order::getOrderStatus, List.of(ORDER_PENDING_PAY, ORDER_WAIT_DELIVER))
                .set(Order::getOrderStatus, ORDER_CANCELED));
        requireStatusChanged(rows, "取消");

        // 4. 回滚库存：下单时扣了多少就还多少。用原子加法，避免与并发下单互相覆盖
        productMapper.restoreStock(order.getProductId(), order.getQuantity());
    }

    /**
     * 判断用户是否为订单的参与方（买家或卖家）。
     */
    private boolean isParticipant(Order order, Long userId) {
        return Objects.equals(order.getBuyerId(), userId)
                || Objects.equals(order.getSellerId(), userId);
    }

    /**
     * 取出订单并要求当前用户是买家。
     *
     * <p>越权时**故意不区分**「这不是你的订单」与「订单不存在」——
     * 区分开就等于提供了一个按订单号探测他人订单是否存在的接口。</p>
     */
    private Order requireBuyerOrder(Long buyerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return order;
    }

    /**
     * 取出订单并要求当前用户是该订单的卖家（农户）。
     */
    private Order requireSellerOrder(Long sellerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getSellerId(), sellerId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return order;
    }

    /**
     * 状态流转的并发兜底：受影响行数为 0 说明前置状态已不成立，即状态被别的请求改掉了。
     *
     * <p>与审核模块 {@code applyProductAudit} 的「商品状态已变更，请刷新后重试」同一套处理方式。</p>
     */
    private void requireStatusChanged(int rows, String action) {
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "订单状态已变更，" + action + "未生效，请刷新后重试");
        }
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
        // 收货信息（下单时快照）。历史订单这几列为 NULL，前端按「未填写」展示
        vo.setConsignee(order.getConsignee());
        vo.setConsigneePhone(order.getConsigneePhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setOrderRemark(order.getOrderRemark());
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
        vo.setOrigin(product.getOrigin());
        vo.setSellerId(product.getSellerId());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setStatus(product.getStatus());
        return vo;
    }

    /**
     * 「我的商品」列表专用：在列表字段之外补上 description 与 images。
     *
     * <p>为什么不让 {@link #convertToProductVO} 一并带上：那个方法是前台商品列表
     * 共用的（一页 12 条），带上 500 字的描述只是白白撑大响应体。
     * 而农户的编辑表单必须回显这两项 —— 不回显的话，农户点「编辑」看到的是空描述框，
     * 一保存就把原文案清掉了，而保存又会触发重新审核、把商品下架。</p>
     */
    private ProductVO convertToMyProductVO(Product product) {
        ProductVO vo = convertToProductVO(product);
        vo.setDescription(product.getDescription());
        vo.setImages(product.getImages());
        return vo;
    }
}
