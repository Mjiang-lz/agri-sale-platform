package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.OrderCreateDTO;
import com.rxt.entity.Cart;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.mapper.CartMapper;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.CommentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M2 农产品交易的「钱与货」核心逻辑单元测试。
 *
 * <p><b>为什么这些点值得单独守护</b>：下面每一条都属于「就算写错也不会报错、
 * 不会打日志，只是安静地把数据写坏」的类型，靠人工评审和联调都很难发现：</p>
 *
 * <ul>
 *   <li><b>防超卖</b>：{@code deductStock} 是
 *       {@code UPDATE t_product SET stock = stock - #{q} WHERE product_id = ? AND stock >= #{q}}
 *       一条原子语句，返回 0 表示没扣到。此时必须抛 400 且<b>不得插入订单</b>——
 *       少了这个判断，要么卖出比库存多的货，要么产生「库存没扣、订单却生成了」的白条订单。
 *       <br>⚠️ 不能改成「先 selectById 读 stock 判断、再 updateById 回写」：那是读-改-写，
 *       两个并发下单会读到同一个旧库存而双双通过。</li>
 *   <li><b>购买数量下界</b>：扣减 SQL 形如 {@code stock = stock - quantity}，
 *       传负数会让 {@code stock >= -5} 恒成立，库存<b>不减反增</b>，订单金额也变成负数。
 *       Controller 的 {@code @Min(1)} 已拦过一次，这里守的是 Service 作为模块对内入口的第二道闸。</li>
 *   <li><b>状态流转的并发兜底</b>：支付/发货/收货/取消都写成
 *       {@code UPDATE ... WHERE order_status = '前置状态'}，靠 {@code rows == 0} 挡住并发重复提交。
 *       开头那段 {@code if} 只负责给友好文案，<b>光靠它是挡不住并发的</b>——
 *       两个请求同时读到「待支付」会双双通过。</li>
 *   <li><b>取消订单的库存回滚</b>：回滚必须发生在状态<b>确实变更之后</b>。
 *       若「已取消」分支被漏掉，重复取消会把库存<b>凭空加回来</b>——
 *       这是最隐蔽的一类库存错误，往往等到对账对不上才暴露。</li>
 *   <li><b>越权</b>：非买家操作订单、改删他人购物车，一律 404 且与「资源不存在」<b>同文案</b>。
 *       若区分开「这不是你的」与「不存在」，就等于对外提供了一个按 id
 *       探测他人订单/购物车条目是否存在的接口。</li>
 * </ul>
 *
 * <p>纯 Mockito，不启动 Spring、不依赖本地 MySQL，谁 clone 下来都能直接跑。</p>
 *
 * <p><b>本类测不到的</b>：{@code @Transactional} 的回滚（需要 Spring 容器才生效），
 * 以及 {@code deductStock} 那条 UPDATE 在真库上的原子性（需要真库并发）。
 * 这两项属于集成测试范围，不在这里假装测过。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M2 交易 · 下单防超卖 / 订单状态流转 / 越权")
class TradeServiceImplTest {

    /**
     * 让 MyBatis-Plus 认识这些实体。被测代码用了
     * {@code LambdaUpdateWrapper<Order>}（lambda → 列名映射），而这份映射来自
     * {@code TableInfo} 缓存，缓存是 MyBatis 启动扫描实体时建立的 ——
     * 纯 Mockito 不起容器，缓存为空会报
     * {@code can not find lambda cache for this entity}。手动补一次即可。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(Product.class, Order.class, Cart.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    /** 买家（采购方） */
    private static final Long BUYER = 5L;

    /** 卖家（农户） */
    private static final Long SELLER = 7L;

    /** 与本单无关的第三方 */
    private static final Long OUTSIDER = 9L;

    private static final Long ORDER_ID = 100L;
    private static final Long PRODUCT_ID = 41L;
    private static final Long CART_ID = 11L;

    /** 订单状态常量，取值与 t_order.order_status 一致 */
    private static final String PENDING_PAY = "待支付";
    private static final String WAIT_DELIVER = "待发货";
    private static final String WAIT_RECEIVE = "待收货";
    private static final String FINISHED = "已完成";
    private static final String CANCELED = "已取消";

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

    @InjectMocks
    private TradeServiceImpl tradeService;

    // ---------------- 测试夹具 ----------------

    /** 一件可正常购买的商品：已过审、已上架、单价 12.50 */
    private Product sellableProduct() {
        Product p = new Product();
        p.setProductId(PRODUCT_ID);
        p.setSellerId(SELLER);
        p.setPrice(new BigDecimal("12.50"));
        p.setStock(100);
        p.setAuditStatus("已通过");
        p.setStatus(1);
        return p;
    }

    /** 指定状态的订单：买家 BUYER、卖家 SELLER、买 3 件 */
    private Order orderWithStatus(String status) {
        Order o = new Order();
        o.setOrderId(ORDER_ID);
        o.setBuyerId(BUYER);
        o.setSellerId(SELLER);
        o.setProductId(PRODUCT_ID);
        o.setQuantity(3);
        o.setOrderStatus(status);
        return o;
    }

    private OrderCreateDTO orderDto(Integer quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(PRODUCT_ID);
        dto.setQuantity(quantity);
        return dto;
    }

    private Cart cartOf(Long cartId, Long buyerId) {
        Cart c = new Cart();
        c.setCartId(cartId);
        c.setBuyerId(buyerId);
        c.setProductId(PRODUCT_ID);
        c.setQuantity(2);
        return c;
    }

    /** 捕获抛出的 BizException，顺带断言类型与状态码 */
    private BizException expectBizException(int expectedCode, Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run,
                "该场景应当抛出 BizException（期望 code=" + expectedCode + "）");
        assertEquals(expectedCode, ex.getCode(),
                "状态码不对。业务规则不满足应为 400；若落到 500 会被当成服务端故障。实际：" + ex.getCode());
        return ex;
    }

    // ==================== 一、下单：防超卖与参数闸门 ====================

    @Test
    @DisplayName("下单：数量为 null / 0 / 负数一律 400，且完全不碰库存")
    void createOrder_invalidQuantity_rejectedBeforeTouchingStock() {
        for (Integer bad : new Integer[]{null, 0, -5}) {
            expectBizException(ResultCode.BAD_REQUEST.getCode(),
                    () -> tradeService.createOrder(BUYER, orderDto(bad)));
        }
        // 负数最危险：stock = stock - (-5) 会让库存不减反增，金额也变负
        verify(productMapper, never()).deductStock(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单：商品不存在 → 400，且不扣库存")
    void createOrder_productNotFound_throws400() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.createOrder(BUYER, orderDto(3)));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单：商品未过审 → 400（待审核的商品不能被下单）")
    void createOrder_productNotApproved_throws400() {
        Product pending = sellableProduct();
        pending.setAuditStatus("待审核");
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(pending);

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.createOrder(BUYER, orderDto(3)));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("下单：商品已下架 → 400")
    void createOrder_productOffShelf_throws400() {
        Product off = sellableProduct();
        off.setStatus(0);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(off);

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.createOrder(BUYER, orderDto(3)));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("★ 下单：扣减库存返回 0（被并发抢空）→ 400，且绝不允许生成订单")
    void createOrder_stockInsufficient_throws400AndInsertsNoOrder() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(sellableProduct());
        // 原子扣减影响 0 行 = 库存不足（或商品刚被删）
        when(productMapper.deductStock(PRODUCT_ID, 3)).thenReturn(0);

        BizException ex = expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.createOrder(BUYER, orderDto(3)));

        assertTrue(ex.getMessage().contains("库存不足"),
                "文案应提示库存不足，实际：" + ex.getMessage());
        // 扣不到库存却把订单写进去 = 白条订单：卖了并不存在的货，且永不回滚
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单成功：卖家取自商品、单价落快照、金额=单价×数量、状态=待支付、订单号以 RX 开头")
    void createOrder_success_snapshotsPriceAndStatus() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(sellableProduct());
        when(productMapper.deductStock(PRODUCT_ID, 3)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            // 模拟自增主键回填，才能断言返回值
            invocation.getArgument(0, Order.class).setOrderId(ORDER_ID);
            return 1;
        });

        Long newOrderId = tradeService.createOrder(BUYER, orderDto(3));

        assertEquals(ORDER_ID, newOrderId, "应返回新生成的订单 id");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();

        assertEquals(BUYER, saved.getBuyerId());
        assertEquals(SELLER, saved.getSellerId(), "卖家必须取自商品记录，不能由前端传入");
        assertEquals(PRODUCT_ID, saved.getProductId());
        assertEquals(3, saved.getQuantity());
        assertEquals(0, new BigDecimal("12.50").compareTo(saved.getUnitPrice()),
                "单价要落快照，否则卖家后续改价会影响历史订单金额");
        assertEquals(0, new BigDecimal("37.50").compareTo(saved.getOrderAmount()),
                "订单金额应为 单价 × 数量");
        assertEquals(PENDING_PAY, saved.getOrderStatus(), "新订单初始状态必须是「待支付」");
        assertNotNull(saved.getOrderNo());
        assertTrue(saved.getOrderNo().startsWith("RX"),
                "订单号格式应为 RX + 时间 + 随机数，实际：" + saved.getOrderNo());
        verify(productMapper).deductStock(PRODUCT_ID, 3);
    }

    // ==================== 二、支付 ====================

    @Test
    @DisplayName("支付：非「待支付」状态 → 400，且不产生任何更新")
    void payOrder_wrongStatus_throws400() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(WAIT_DELIVER));

        BizException ex = expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.payOrder(BUYER, ORDER_ID));

        assertTrue(ex.getMessage().contains(WAIT_DELIVER),
                "文案要带上当前状态，前端才能解释清楚为什么不能付。实际：" + ex.getMessage());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★ 支付：条件更新影响 0 行（并发重复提交）→ 400")
    void payOrder_concurrentDuplicate_throws400() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(PENDING_PAY));
        // 前面那段 if 看到的是「待支付」，但在发出 UPDATE 的瞬间状态已被另一个请求改掉
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        BizException ex = expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.payOrder(BUYER, ORDER_ID));

        assertTrue(ex.getMessage().contains("状态已变更"),
                "并发重复提交必须被 rows==0 兜住。实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("支付：非买家本人 → 404，且文案与「订单不存在」完全一致")
    void payOrder_notBuyer_throws404() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(PENDING_PAY));

        BizException ex = expectBizException(ResultCode.NOT_FOUND.getCode(),
                () -> tradeService.payOrder(OUTSIDER, ORDER_ID));

        assertTrue(ex.getMessage().contains("订单不存在"),
                "越权必须与「不存在」同文案，否则可用来探测他人订单是否存在。实际：" + ex.getMessage());
        verify(orderMapper, never()).update(any(), any());
    }

    // ==================== 三、发货 ====================

    @Test
    @DisplayName("发货：先校验农户角色；非「待发货」→ 400 且不更新")
    void deliverOrder_checksFarmerRoleAndStatus() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(PENDING_PAY));

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.deliverOrder(SELLER, ORDER_ID));

        // 角色校验是发货的专属前置：不是农户就不该走到业务逻辑
        verify(permissionChecker).requireRole(RoleConstant.FARMER);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★ 发货：条件更新影响 0 行（并发）→ 400")
    void deliverOrder_concurrentDuplicate_throws400() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(WAIT_DELIVER));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.deliverOrder(SELLER, ORDER_ID));
    }

    @Test
    @DisplayName("发货：非卖家本人 → 404")
    void deliverOrder_notSeller_throws404() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(WAIT_DELIVER));

        expectBizException(ResultCode.NOT_FOUND.getCode(),
                () -> tradeService.deliverOrder(OUTSIDER, ORDER_ID));

        verify(orderMapper, never()).update(any(), any());
    }

    // ==================== 四、收货 ====================

    @Test
    @DisplayName("收货：非「待收货」→ 400；「待收货」则推进到已完成")
    void receiveOrder_statusGuard() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(WAIT_DELIVER));
        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.receiveOrder(BUYER, ORDER_ID));
        verify(orderMapper, never()).update(any(), any());

        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(WAIT_RECEIVE));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        tradeService.receiveOrder(BUYER, ORDER_ID);
        verify(orderMapper).update(isNull(), any());
    }

    // ==================== 五、取消：库存回滚的三个陷阱 ====================

    @Test
    @DisplayName("取消：已发货（待收货/已完成）→ 400，且不回滚库存")
    void cancelOrder_shippedOrder_throws400() {
        for (String shipped : new String[]{WAIT_RECEIVE, FINISHED}) {
            when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(shipped));
            expectBizException(ResultCode.BAD_REQUEST.getCode(),
                    () -> tradeService.cancelOrder(BUYER, ORDER_ID));
        }
        verify(productMapper, never()).restoreStock(anyLong(), anyInt());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★ 取消：重复取消 → 400，且绝不能再次回滚库存（否则库存被加两次）")
    void cancelOrder_alreadyCanceled_doesNotRestoreStockAgain() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(CANCELED));

        BizException ex = expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.cancelOrder(BUYER, ORDER_ID));

        assertTrue(ex.getMessage().contains("重复"),
                "应明确提示重复操作。实际：" + ex.getMessage());
        // 若「已取消」分支被漏掉，每次重复点取消都会把库存凭空加回来
        verify(productMapper, never()).restoreStock(anyLong(), anyInt());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★ 取消：条件更新影响 0 行（并发）→ 400，且不回滚库存")
    void cancelOrder_concurrent_throws400WithoutRestore() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(PENDING_PAY));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.cancelOrder(BUYER, ORDER_ID));

        // 状态没改成功就不能回滚，否则与并发的那次取消各回滚一次 → 库存翻倍
        verify(productMapper, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("取消成功：回滚的正是「本单的商品与数量」，且发生在状态变更之后")
    void cancelOrder_success_restoresExactlyThisOrderQuantity() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(PENDING_PAY));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        tradeService.cancelOrder(BUYER, ORDER_ID);

        // 必须是本单的 productId=41 与 quantity=3；回滚错数量同样会写坏库存
        verify(productMapper).restoreStock(PRODUCT_ID, 3);
        verify(orderMapper).update(isNull(), any());
    }

    // ==================== 六、越权：购物车 ====================

    @Test
    @DisplayName("购物车：改他人条目的数量 → 404，且不写入")
    void updateCartQuantity_otherUsersItem_throws404() {
        when(cartMapper.selectById(CART_ID)).thenReturn(cartOf(CART_ID, SELLER));

        expectBizException(ResultCode.NOT_FOUND.getCode(),
                () -> tradeService.updateCartQuantity(BUYER, CART_ID, 5));

        verify(cartMapper, never()).updateById(any(Cart.class));
        // ⚠️ 必须用 anyLong() 而不是 any()：BaseMapper 有 deleteById(Serializable) 与
        // deleteById(T entity) 两个重载，裸 any() 会被推断成后者，验证的是另一个方法 ——
        // 在 never() 场景下会「永远通过」，变成假阴性
        verify(cartMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("购物车：删他人条目 → 404，且不删除")
    void removeCartItem_otherUsersItem_throws404() {
        when(cartMapper.selectById(CART_ID)).thenReturn(cartOf(CART_ID, SELLER));

        expectBizException(ResultCode.NOT_FOUND.getCode(),
                () -> tradeService.removeCartItem(BUYER, CART_ID));

        verify(cartMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("购物车：数量为 0 视为删除条目（而不是写入一条数量为 0 的记录）")
    void updateCartQuantity_zeroMeansRemove() {
        when(cartMapper.selectById(CART_ID)).thenReturn(cartOf(CART_ID, BUYER));

        tradeService.updateCartQuantity(BUYER, CART_ID, 0);

        verify(cartMapper).deleteById(anyLong());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("购物车：数量为负 → 400，且完全不访问购物车表")
    void updateCartQuantity_negative_throws400() {
        expectBizException(ResultCode.BAD_REQUEST.getCode(),
                () -> tradeService.updateCartQuantity(BUYER, CART_ID, -1));

        verifyNoInteractions(cartMapper);
    }
}
