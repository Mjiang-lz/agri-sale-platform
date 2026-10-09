import request from './request'

/**
 * 农产品交易（trade 模块）接口文件。
 *
 * 对应页面：
 * - frontend-web/src/views/trade/ProductList.vue   商品货源
 * - frontend-web/src/views/trade/ProductDetail.vue 商品详情
 * - frontend-web/src/views/trade/DemandList.vue   求购需求
 * - frontend-web/src/views/trade/Cart.vue         购物车
 * - frontend-web/src/views/trade/OrderList.vue    我的订单
 *
 * 后端基础路径已由 request.js 设置为 /api，这里只写相对路径。
 * 注意两个容易踩的坑：
 *  1. 分页参数是 pageNum / pageSize（不是 finance 模块的 page / size），
 *     TradeController 与 DemandController 都用 pageNum / pageSize。
 *  2. 路径是单数 order（/trade/order/list），复数 orders 在后端不存在。
 */

/* ====================================================================
 * 商品货源
 * ==================================================================== */

/**
 * 分页检索商品货源（公开接口，游客可访问）。
 * 四个筛选条件可任意组合，不传即返回在架全部商品。
 * 返回 MyBatis-Plus Page：{ current, pages, records, total, ... }
 */
export function getTradeProducts(params = {}) {
  const { pageNum = 1, pageSize = 12, keyword, category, minPrice, maxPrice, origin } = params
  return request.get('/trade/products', {
    params: {
      pageNum,
      pageSize,
      // 空字符串会被 axios 序列化成 keyword= ，后端 required=false 能接住，
      // 但传 undefined 才是「不带这个参数」，这里统一过滤掉空值
      ...(keyword ? { keyword } : {}),
      ...(category ? { category } : {}),
      ...(minPrice !== '' && minPrice !== null && minPrice !== undefined ? { minPrice } : {}),
      ...(maxPrice !== '' && maxPrice !== null && maxPrice !== undefined ? { maxPrice } : {}),
      ...(origin ? { origin } : {})
    }
  })
}

/** 商品详情：商品全字段 + 库存 + 发布农户公开信息 + 已过审评论。只返回审核通过的商品。 */
export function getTradeProductDetail(productId) {
  return request.get(`/trade/products/${productId}`)
}

/* ====================================================================
 * 求购需求
 * ==================================================================== */

/**
 * 分页查询求购需求（公开接口，游客可访问）。
 * 注意路径是 /demand/list，不是 /trade/demands —— 后端没有 /trade/demands。
 */
export function getDemandList(params = {}) {
  const { pageNum = 1, pageSize = 10, category, region } = params
  return request.get('/demand/list', {
    params: {
      pageNum,
      pageSize,
      ...(category ? { category } : {}),
      ...(region ? { region } : {})
    }
  })
}

/** 求购需求详情。注意该路径要求登录。 */
export function getDemandDetail(id) {
  return request.get(`/demand/${id}`)
}

/* ====================================================================
 * 购物车
 * ==================================================================== */

/** 查询我的购物车（分页）。身份取自登录态，不接受 buyerId 参数。 */
export function getCartList(pageNum = 1, pageSize = 50) {
  return request.get('/trade/cart/list', { params: { pageNum, pageSize } })
}

/** 加入购物车。productId / quantity 走 query 参数，后端用 @RequestParam 接收。 */
export function addToCart(productId, quantity = 1) {
  return request.post('/trade/cart', null, { params: { productId, quantity } })
}

/** 修改购物车条目数量。quantity 传 0 表示删除该条目（后端约定）。 */
export function updateCartQuantity(cartId, quantity) {
  return request.put(`/trade/cart/${cartId}`, null, { params: { quantity } })
}

/** 删除购物车条目。 */
export function removeCartItem(cartId) {
  return request.delete(`/trade/cart/${cartId}`)
}

/* ====================================================================
 * 订单
 * ==================================================================== */

/**
 * 查询我的订单（分页），可按状态筛选。
 * orderStatus 取值：待支付 / 待发货 / 待收货 / 已完成 / 已取消
 */
export function getOrderList(params = {}) {
  const { pageNum = 1, pageSize = 10, orderStatus } = params
  return request.get('/trade/order/list', {
    params: { pageNum, pageSize, ...(orderStatus ? { orderStatus } : {}) }
  })
}

/** 查询我卖出的订单（农户视角），发货入口。 */
export function getSoldOrderList(params = {}) {
  const { pageNum = 1, pageSize = 10, orderStatus } = params
  return request.get('/trade/order/sold', {
    params: { pageNum, pageSize, ...(orderStatus ? { orderStatus } : {}) }
  })
}

/** 订单详情。买家与卖家都能查看。 */
export function getOrderDetail(orderId) {
  return request.get(`/trade/order/${orderId}`)
}

/** 支付订单：待支付 → 待发货。无请求体。 */
export function payOrder(orderId) {
  return request.post(`/trade/order/${orderId}/pay`)
}

/** 发货：待发货 → 待收货。仅农户（卖家）可操作。 */
export function deliverOrder(orderId) {
  return request.post(`/trade/order/${orderId}/deliver`)
}

/** 确认收货：待收货 → 已完成。仅买家可操作。 */
export function receiveOrder(orderId) {
  return request.post(`/trade/order/${orderId}/receive`)
}

/** 取消订单：仅「待支付 / 待发货」可取消。 */
export function cancelOrder(orderId) {
  return request.post(`/trade/order/${orderId}/cancel`)
}
