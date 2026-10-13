package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rxt.common.BizException;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.entity.Cart;
import com.rxt.entity.Demand;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.mapper.CartMapper;
import com.rxt.mapper.DemandMapper;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.service.AddressService;
import com.rxt.service.CommentService;
import com.rxt.service.DemandExpireService;
import com.rxt.service.NotificationService;
import com.rxt.vo.AddressVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务错误码一致性回归测试（M2 交易购物车 / 下单、M3 求购）。
 *
 * <p><b>这条闸门守的是什么</b>：{@code docs/接口约定-M2订单流转.md} §五 约定
 * 「参数错误 → 400、无权限 → 403、资源不存在 → 404」。而 {@code new BizException(String)}
 * 会统一落到 {@code BIZ_ERROR(500)}，把「调用方改一下就能过」的问题（换商品、少买点、
 * 改自己的需求）和真正的服务端故障混成同一类，污染日志排查与前端提示。</p>
 *
 * <p>本类只断言 <b>code</b>（400/403/404），不断言文案 —— 文案可改，码不能变。</p>
 *
 * <p><b>为什么用纯 Mockito 不起 Spring</b>：与 {@code AuditServiceImplTest} 保持一致，
 * 不需要本地 MySQL、不受 Spring Boot 4 测试注解变动影响、跑得快。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("业务错误码一致性：可修正的调用方错误必须是 400/403/404，不得落 500")
class ErrorCodeConsistencyTest {

    /**
     * LambdaQueryWrapper 的字段解析依赖 TableInfo 缓存，未初始化会抛
     * 「can not find lambda cache」。这里只初始化本类会走到的实体。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(Product.class, Cart.class, Order.class, Demand.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    private static final String APPROVED = "已通过";
    private static final int ON = 1;

    @Mock
    private ProductMapper productMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FarmerProfileMapper farmerProfileMapper;
    @Mock
    private CommentService commentService;
    @Mock
    private PermissionChecker permissionChecker;
    @Mock
    private DemandMapper demandMapper;
    /**
     * 求购到期自动失效（W2）。10-12 给 DemandServiceImpl 加了这一步之后，
     * 本类有三个用例（详情 / 编辑 / 关闭）开始以 NPE 失败 —— 当时只跑了
     * DemandServiceImplTest，没跑本类，所以一直没被发现。
     */
    @Mock
    private DemandExpireService demandExpireService;
    /** 收货地址校验（10-10 组长加入 createOrder） */
    @Mock
    private AddressService addressService;
    /** 站内通知（订单状态流转写入） */
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TradeServiceImpl tradeService;
    @InjectMocks
    private DemandServiceImpl demandService;

    /**
     * 下单用例需要一笔合法地址。放 {@code @BeforeEach} + lenient：
     * 本类的用例大多不下单，严格模式会把它们全判成「多余 stub」。
     */
    @BeforeEach
    void stubPickableAddress() {
        lenient().when(addressService.loadOwned(anyLong(), any())).thenReturn(addressFixture());
    }

    private static AddressVO addressFixture() {
        AddressVO vo = new AddressVO();
        vo.setConsignee("刘美玲");
        vo.setPhone("13800000006");
        vo.setProvince("江西省");
        vo.setCity("赣州市");
        vo.setDistrict("信丰县");
        vo.setDetail("嘉定镇 1 号");
        vo.setFullAddress("江西省赣州市信丰县嘉定镇 1 号");
        return vo;
    }

    // ==================== 测试夹具 ====================

    private static Product product(Long id, Long sellerId, int stock, int status, String auditStatus) {
        Product p = new Product();
        p.setProductId(id);
        p.setSellerId(sellerId);
        p.setProductName("测试商品");
        p.setPrice(new BigDecimal("12.50"));
        p.setStock(stock);
        p.setStatus(status);
        p.setAuditStatus(auditStatus);
        return p;
    }

    private static Cart cart(Long cartId, Long buyerId, Long productId, int quantity) {
        Cart c = new Cart();
        c.setCartId(cartId);
        c.setBuyerId(buyerId);
        c.setProductId(productId);
        c.setQuantity(quantity);
        c.setTotalAmount(new BigDecimal("12.50").multiply(BigDecimal.valueOf(quantity)));
        return c;
    }

    private static Demand demand(Long demandId, Long publisherId, int status) {
        Demand d = new Demand();
        d.setDemandId(demandId);
        d.setPublisherId(publisherId);
        d.setCategory("水稻");
        d.setQuantity(100);
        d.setRegion("黑龙江");
        d.setValidUntil(LocalDateTime.now().plusDays(30));
        d.setStatus(status);
        return d;
    }

    private static DemandPublishDTO demandDto() {
        DemandPublishDTO dto = new DemandPublishDTO();
        dto.setCategory("水稻");
        dto.setQuantity(100);
        dto.setRegion("黑龙江");
        dto.setValidUntil(LocalDateTime.now().plusDays(30));
        dto.setContact("13800000000");
        return dto;
    }

    private static OrderCreateDTO orderDto(Long productId, Integer quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        return dto;
    }

    /** 断言抛出的是 BizException 且 code 等于期望值（而非 BIZ_ERROR=500）。 */
    private static BizException assertCode(int expected, Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run);
        assertEquals(expected, ex.getCode(),
                "错误码应为 " + expected + "，实际 " + ex.getCode() + "（文案：" + ex.getMessage() + "）");
        return ex;
    }

    // ==================== M3 求购 ====================

    @Test
    @DisplayName("求购详情：需求不存在 → 404（不是 500）")
    void demandDetail_notFound() {
        // demandMapper.selectById 默认返回 null → 查不到
        assertCode(NOT_FOUND, () -> demandService.detail(999L));
    }

    @Test
    @DisplayName("编辑求购：需求不存在 → 404")
    void updateDemand_notFound() {
        assertCode(NOT_FOUND, () -> demandService.updateDemand(5L, 999L, demandDto()));
        verify(demandMapper, never()).updateById(any(Demand.class));
    }

    @Test
    @DisplayName("编辑求购：非发布者本人 → 403（不是 500）")
    void updateDemand_notOwner() {
        when(demandMapper.selectById(1L)).thenReturn(demand(1L, 5L, 1));
        assertCode(FORBIDDEN, () -> demandService.updateDemand(7L, 1L, demandDto()));
        verify(demandMapper, never()).updateById(any(Demand.class));
    }

    @Test
    @DisplayName("关闭求购：需求不存在 → 404")
    void closeDemand_notFound() {
        assertCode(NOT_FOUND, () -> demandService.closeDemand(5L, 999L));
        verify(demandMapper, never()).updateById(any(Demand.class));
    }

    @Test
    @DisplayName("关闭求购：非发布者本人 → 403（不是 500）")
    void closeDemand_notOwner() {
        when(demandMapper.selectById(1L)).thenReturn(demand(1L, 5L, 1));
        assertCode(FORBIDDEN, () -> demandService.closeDemand(7L, 1L));
        verify(demandMapper, never()).updateById(any(Demand.class));
    }

    @Test
    @DisplayName("回归：发布者本人关闭求购 → 正常置为已关闭")
    void closeDemand_owner_success() {
        Demand d = demand(1L, 5L, 1);
        when(demandMapper.selectById(1L)).thenReturn(d);

        demandService.closeDemand(5L, 1L);

        assertEquals(0, d.getStatus());
        verify(demandMapper).updateById(d);
    }

    @Test
    @DisplayName("回归：发布者本人编辑求购 → 正常写入")
    void updateDemand_owner_success() {
        Demand d = demand(1L, 5L, 1);
        when(demandMapper.selectById(1L)).thenReturn(d);

        demandService.updateDemand(5L, 1L, demandDto());

        verify(demandMapper).updateById(d);
    }

    // ==================== M2 购物车 ====================

    @Test
    @DisplayName("加购物车：商品不存在 → 400（不是 500）")
    void addToCart_productNotFound() {
        // productMapper.selectById 默认返回 null
        assertCode(BAD_REQUEST, () -> tradeService.addToCart(7L, 999L, 1));
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    @DisplayName("加购物车：商品未上架 → 400")
    void addToCart_productOffShelf() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, 0, APPROVED));
        assertCode(BAD_REQUEST, () -> tradeService.addToCart(7L, 22L, 1));
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    @DisplayName("加购物车：商品未通过审核 → 400")
    void addToCart_productNotApproved() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, ON, "待审核"));
        assertCode(BAD_REQUEST, () -> tradeService.addToCart(7L, 22L, 1));
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    @DisplayName("加购物车：库存不足 → 400，且不写库")
    void addToCart_outOfStock() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 3, ON, APPROVED));
        assertCode(BAD_REQUEST, () -> tradeService.addToCart(7L, 22L, 10));
        verify(cartMapper, never()).insert(any(Cart.class));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("回归：加购物车 正常 → 新增条目")
    void addToCart_success() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, ON, APPROVED));

        tradeService.addToCart(7L, 22L, 3);

        verify(cartMapper).insert(any(Cart.class));
    }

    @Test
    @DisplayName("改购物车数量：数量为负 → 400，且不触碰任何表")
    void updateCartQuantity_negative() {
        assertCode(BAD_REQUEST, () -> tradeService.updateCartQuantity(7L, 1L, -1));
        verify(cartMapper, never()).updateById(any(Cart.class));
        verify(cartMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("改购物车数量：条目不存在 → 404（不是 500）")
    void updateCartQuantity_notFound() {
        assertCode(NOT_FOUND, () -> tradeService.updateCartQuantity(7L, 999L, 2));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("改购物车数量：条目属于他人 → 404（不区分「不存在」与「不是你的」）")
    void updateCartQuantity_notOwner() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 5L, 22L, 2));
        assertCode(NOT_FOUND, () -> tradeService.updateCartQuantity(7L, 1L, 3));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("改购物车数量：商品已下架 → 400")
    void updateCartQuantity_productOffShelf() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 7L, 22L, 2));
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, 0, APPROVED));
        assertCode(BAD_REQUEST, () -> tradeService.updateCartQuantity(7L, 1L, 3));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("改购物车数量：库存不足 → 400")
    void updateCartQuantity_outOfStock() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 7L, 22L, 2));
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 1, ON, APPROVED));
        assertCode(BAD_REQUEST, () -> tradeService.updateCartQuantity(7L, 1L, 3));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("回归：改购物车数量 正常 → 写入新数量")
    void updateCartQuantity_success() {
        Cart c = cart(1L, 7L, 22L, 2);
        when(cartMapper.selectById(1L)).thenReturn(c);
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, ON, APPROVED));

        tradeService.updateCartQuantity(7L, 1L, 3);

        assertEquals(3, c.getQuantity());
        verify(cartMapper).updateById(c);
    }

    @Test
    @DisplayName("回归：数量为 0 视为删除该条目")
    void updateCartQuantity_zeroMeansDelete() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 7L, 22L, 2));

        tradeService.updateCartQuantity(7L, 1L, 0);

        verify(cartMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删购物车：条目不存在 → 404")
    void removeCartItem_notFound() {
        assertCode(NOT_FOUND, () -> tradeService.removeCartItem(7L, 999L));
        verify(cartMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删购物车：条目属于他人 → 404")
    void removeCartItem_notOwner() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 5L, 22L, 2));
        assertCode(NOT_FOUND, () -> tradeService.removeCartItem(7L, 1L));
        verify(cartMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("回归：删购物车 正常 → 删除条目")
    void removeCartItem_success() {
        when(cartMapper.selectById(1L)).thenReturn(cart(1L, 7L, 22L, 2));

        tradeService.removeCartItem(7L, 1L);

        verify(cartMapper).deleteById(1L);
    }

    // ==================== M2 下单 ====================

    @Test
    @DisplayName("下单：数量为 null → 400")
    void createOrder_quantityNull() {
        assertCode(BAD_REQUEST, () -> tradeService.createOrder(7L, orderDto(22L, null)));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单：数量为 0 → 400（负数会让库存不减反增）")
    void createOrder_quantityZero() {
        assertCode(BAD_REQUEST, () -> tradeService.createOrder(7L, orderDto(22L, 0)));
        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("下单：商品不存在 → 400")
    void createOrder_productNotFound() {
        assertCode(BAD_REQUEST, () -> tradeService.createOrder(7L, orderDto(999L, 1)));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单：扣减库存影响行数为 0 → 400 库存不足")
    void createOrder_outOfStock() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 1, ON, APPROVED));
        when(productMapper.deductStock(22L, 10)).thenReturn(0);
        assertCode(BAD_REQUEST, () -> tradeService.createOrder(7L, orderDto(22L, 10)));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("回归：下单 正常 → 返回订单号且写入订单")
    void createOrder_success() {
        when(productMapper.selectById(22L)).thenReturn(product(22L, 5L, 80, ON, APPROVED));
        when(productMapper.deductStock(22L, 3)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            inv.<Order>getArgument(0).setOrderId(888L);
            return 1;
        });

        Long orderId = tradeService.createOrder(7L, orderDto(22L, 3));

        assertEquals(888L, orderId);
        verify(orderMapper).insert(any(Order.class));
    }

    /**
     * 闸门自检：证明本测试<b>真的能抓住回归</b>。
     *
     * <p>把上面任何一处 {@code new BizException(ResultCode.XXX.getCode(), "…")} 改回
     * {@code new BizException("…")}，对应的 400/403/404 断言就会变成 500 而失败 ——
     * 这条自检把「默认码究竟是什么」钉死，避免有人改了 {@code BizException} 的默认值后
     * 整组断言静默失效。</p>
     */
    @Test
    @DisplayName("闸门自检：旧写法 BizException(String) 落在 500 —— 与上面断言的 4xx 形成对照")
    void gate_selfCheck_plainBizExceptionIs500() {
        BizException legacy = new BizException("商品不存在或已下架");
        assertEquals(500, legacy.getCode(),
                "若此断言失败，说明 BizException(String) 的默认码变了，本类所有 400/403/404 断言需重新审视");
    }
}
