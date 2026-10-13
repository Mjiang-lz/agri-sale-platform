import request from './request'

/**
 * 农产品交易（trade 模块）接口文件。
 *
 * 对应页面：
 * - frontend-web/src/views/trade/ProductList.vue   商品货源
 * - frontend-web/src/views/trade/ProductDetail.vue 商品详情
 * - frontend-web/src/views/trade/DemandList.vue   求购需求
 * - frontend-web/src/views/trade/Cart.vue         购物车
 * - frontend-web/src/views/trade/Checkout.vue     结算确认
 * - frontend-web/src/views/trade/OrderList.vue    我的订单
 * - frontend-web/src/views/trade/MyProducts.vue   我的商品（农户）
 * - frontend-web/src/views/user/Address.vue       收货地址
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

/**
 * 品类字典（公开接口，游客可访问）。
 * 返回 [{ category, productCount }]，按在架商品数降序。
 *
 * ★ 这是「分类要覆盖所有商品」的唯一真源。此前四个视图各自硬编码了
 *   同一份六类字符串（蔬菜/水果/粮油/干货/水产/畜禽产品），谁发布一个
 *   「茶叶」的商品，它就成了在下拉与首页入口里都点不到的孤儿品类。
 *   只有从库里 distinct 出来才算覆盖。
 */
export function getTradeCategories() {
  return request.get('/trade/categories')
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

/**
 * 关闭求购（我的发布 → 求购需求管理）。
 *
 * 语义是「这条需求不收了 / 已达成」，**行保留**、status 由 1 → 0、从公开列表消失。
 * 与 removeDemand 不是一回事，两个按钮不能合并：
 *   · 关闭 = 可逆的「撤下」，还能再编辑回来
 *   · 删除 = 不可逆的「移除记录」
 * 幂等：重复关闭同一条也返回成功。
 */
export function closeDemand(id) {
  return request.post(`/demand/${id}/close`)
}

/**
 * 删除求购（我的发布 → 求购需求管理）。
 *
 * ⚠️ **不可逆**，且与商品删除不同 —— 这里一律物理删行。
 * 理由：SRS 的软删例外只针对「已被订单引用的商品」，求购不参与下单链路，
 * 全库没有任何外键指向 t_demand，删行不会留下悬空引用。
 * 调用方必须先二次确认。
 *
 * 只能删自己发布的，删他人返回 403（body 里的 code，HTTP 恒为 200）。
 */
export function removeDemand(id) {
  return request.delete(`/demand/${id}`)
}

/* ====================================================================
 * 发布 / 编辑求购需求
 *
 * ⚠️⚠️ 下面两个接口**后端目前不可用**，前端已做成可控降级，请勿直接删掉 mock 分支。
 *
 * 实测结论（2026-10-12，curl 直接打后端）：
 *   POST /demand/publish  → 500「系统繁忙，请稍后重试」
 *   PUT  /demand/{id}     → 同样 500
 *   但 DELETE /demand/{id} → 200 且真删掉了行。
 *
 * 定位过程（供队友排查参考）：
 *   1) 用等价 SQL 直连 rxt 库手动 INSERT —— 成功，说明表结构与字段都没问题；
 *   2) 故意缺必填字段 category —— 返回 **500 而非 400**，
 *      说明 @Valid 的校验压根没跑，请求死在 JSON 反序列化阶段，方法体没执行；
 *   3) GlobalExceptionHandler 已有 MethodArgumentNotValidException 分支，
 *      但**没有 HttpMessageNotReadableException 分支**，
 *      而 validUntil 是 LocalDateTime —— 时间解析失败正好落进兜底
 *      Exception 分支，被翻译成「系统繁忙」。
 *   推测真因：LocalDateTime 缺 @JsonFormat 或全局 Jackson 配置不匹配。
 *
 * 前端对策：publishDemand / updateDemand 走 mock，交互完整可用，
 * 等后端修好后把 USE_DEMAND_MOCK 置 false（或删掉 mock 分支）即自动切到真实接口。
 * ==================================================================== */

/** 发布/编辑求购走mock：后端 500 时也能把交互跑通、后端一修就自动切真实。 */
export const USE_DEMAND_MOCK = true

/** 把表单里的日期对象拼成后端要的 "YYYY-MM-DD HH:mm:ss"（LocalDateTime 格式）。 */
function formatValidUntil(value) {
  if (!value) return undefined
  if (typeof value === 'string') return value
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return undefined
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** mock 需求库：让右栏与列表在同一页面内自洽（发布后立刻能看到自己那条）。 */
let mockMine = []
let mockSeq = 9000

/**
 * 发布求购需求。
 *
 * @param {object} payload { category, quantity, unit, expectPrice, description, region, validUntil, contact }
 * @returns {Promise<number>} 新建需求的 id
 */
export async function publishDemand(payload) {
  if (!USE_DEMAND_MOCK) {
    return request.post('/demand/publish', {
      ...payload,
      validUntil: formatValidUntil(payload.validUntil)
    })
  }

  // 模拟网络延迟，避免表单按钮的 loading 一闪而过、看不出是假的
  await new Promise((r) => setTimeout(r, 400))
  const id = ++mockSeq
  mockMine.unshift({
    demandId: id,
    publisherId: JSON.parse(localStorage.getItem('rxt_user_profile') || '{}').userId ?? null,
    category: payload.category,
    quantity: payload.quantity,
    unit: payload.unit || '件',
    expectPrice: payload.expectPrice ?? null,
    description: payload.description || '',
    region: payload.region,
    validUntil: formatValidUntil(payload.validUntil),
    contact: payload.contact,
    status: 1,
    createTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
  })
  return id
}

/**
 * 编辑自己发布的求购需求。
 *
 * @param {number|string} id    需求 id
 * @param {object}      payload 同publishDemand
 */
export async function updateDemand(id, payload) {
  if (!USE_DEMAND_MOCK) {
    return request.put(`/demand/${id}`, {
      ...payload,
      validUntil: formatValidUntil(payload.validUntil)
    })
  }

  await new Promise((r) => setTimeout(r, 400))
  const idx = mockMine.findIndex((d) => String(d.demandId) === String(id))
  if (idx > -1) {
    mockMine[idx] = {
      ...mockMine[idx],
      ...payload,
      unit: payload.unit || mockMine[idx].unit,
      validUntil: formatValidUntil(payload.validUntil)
    }
  }
  return id
}

/**
 * 「我发布的」求购需求（mock）。
 *
 * ⚠️ 后端 `GET /demand/list` **不支持按发布者过滤**（只有 category / region），
 * 且强制 status=1 AND valid_until >= NOW()，所以真实接口给不出「我的全部需求」——
 * 已关闭、已过期的一律拿不到。前端只能用当前页数据按 publisherId 过滤，
 * 分页错位会导致「明明发过 3 条，右栏却显示暂无」。
 *
 * 本函数是 mock 版：把本会话发布过的需求补进右栏，
 * 保证「发布 → 能在右栏看到 → 能编辑/关闭/删除」这条闭环先跑通。
 */
export async function getMyDemandsMock() {
  await new Promise((r) => setTimeout(r, 200))
  return mockMine.map((d) => ({ ...d, __mock: true }))
}

/** 关闭：mock 版同步更新本地状态，避免右栏与列表不一致。 */
export async function closeDemandMock(id) {
  if (!USE_DEMAND_MOCK) return closeDemand(id)
  await new Promise((r) => setTimeout(r, 300))
  const hit = mockMine.find((d) => String(d.demandId) === String(id))
  if (hit) hit.status = 0
  return true
}

/** 删除：mock 版从本地移除。 */
export async function removeDemandMock(id) {
  if (!USE_DEMAND_MOCK) return removeDemand(id)
  await new Promise((r) => setTimeout(r, 300))
  mockMine = mockMine.filter((d) => String(d.demandId) !== String(id))
  return true
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
 * 下单。请求体对应 OrderCreateDTO：{ productId, quantity, addressId, orderRemark? }。
 *
 * ⚠️ 本函数此前**缺失**（2026-10-10 补）——
 * Cart.vue 里 `const { createOrder } = await import('@/api/trade')` 拿到的是
 * undefined，`await undefined(...)` 抛 TypeError 并被结算循环里的 catch 吞掉，
 * 表现为「每笔下单都失败」，提示语还是「请检查后端服务与商品库存」，
 * 把前端自己的 bug 报成了后端故障。任何新增下单入口请一律走本函数。
 *
 * addressId 必填：订单要进入农户发货环节，没有收货人的订单农户处理不了。
 * 商品与地址的归属、库存是否充足、商品是否在架，全部由后端裁定 ——
 * 前端传什么就信什么会导致超卖与越权发货，所以这里不做「先查一遍再下单」的乐观校验。
 *
 * @returns {Promise<number>} 新订单主键
 */
export function createOrder(payload) {
  return request.post('/trade/order', payload)
}

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

/* ====================================================================
 * 我的商品（农户侧，对应 TradeController 的 publish / my-products 一组）
 *
 * 这一组后端早在 /trade/product/publish 就实现了，前端一直**没有页面**，
 * 结果「农户登录后没有任何入口发布自己的农产品」——
 * 整条供给侧闭环在演示上是断的（首页展示的全是种子数据）。
 * MyProducts.vue 就是补这个缺口（2026-10-10）。
 *
 * 四个接口都要求「农户」角色（后端 TradeServiceImpl 里 requireRole(FARMER)），
 * 其他角色调用会拿到 HTTP 200 + body code 403。
 * ==================================================================== */

/**
 * 分页查询我发布的商品。
 *
 * 与前台商品列表的区别：这里**不过滤 auditStatus**，
 * 待审核、未通过的商品农户也要看得见，否则提交后无从确认。
 *
 * @param {{pageNum?: number, pageSize?: number, auditStatus?: string}} params
 *        auditStatus 取值：待审核 / 已通过 / 未通过；不传即全部
 */
export function getMyProducts(params = {}) {
  const { pageNum = 1, pageSize = 10, auditStatus } = params
  return request.get('/trade/my-products', {
    params: { pageNum, pageSize, ...(auditStatus ? { auditStatus } : {}) }
  })
}

/**
 * 发布商品。请求体对应 ProductPublishDTO：
 * { productName, category, price, stock, unit?, description?, origin?, imageUrl?, images? }
 *
 * 新发布的商品一律先进「待审核」且默认下架，由后台审核通过时置为在架 ——
 * 所以发布成功后前端不要提示「已上架」，那与事实不符。
 *
 * @returns {Promise<number>} 新商品主键
 */
export function publishProduct(payload) {
  return request.post('/trade/product/publish', payload)
}

/**
 * 编辑商品。请求体与发布同一个 DTO。
 *
 * ⚠️ 后端按「部分更新」处理：**字段为 null 表示本次不修改该字段**。
 * 要清空某个可选字段请传空字符串 `''`（多图传空数组），不要传 null，
 * 这与 MyBatis-Plus 的 updateById 跳过 null 字段的口径一致。
 * 编辑会触发重新审核：内容有变化的商品会被打回「待审核」并下架。
 */
export function updateMyProduct(productId, payload) {
  return request.put(`/trade/products/${productId}`, payload)
}

/**
 * 上下架。status 传 1（上架）或 0（下架）。
 *
 * 只有「已通过」审核的商品才允许上架，否则后端报
 * 「商品当前审核状态为「待审核」，通过审核后才能上架」。
 *
 * status 走 query 参数（后端是 @RequestParam），不是请求体。
 */
export function updateMyProductStatus(productId, status) {
  return request.put(`/trade/products/${productId}/status`, null, { params: { status } })
}

/**
 * 删除我发布的商品（SRS「我的发布：新增、编辑、上架／下架、删除」的最后一项）。
 *
 * ⚠️ **不保证真的删掉了行**，返回值是结果标记（request.js 只透出 Result.data）：
 *   · 'DELETED'   —— 商品已物理删除
 *   · 'OFF_SHELF' —— 该商品已被订单引用，按 SRS 例外流程改为下架，行还在
 * 所以调用方**必须**判返回值再决定提示什么，不能一律报「删除成功」。
 * 详见 docs/接口约定-我的发布.md §2.1。
 */
export function removeMyProduct(productId) {
  return request.delete(`/trade/products/${productId}`)
}

/* ====================================================================
 * 收货地址
 *
 * 归属人一律取自登录态，接口不接受 userId。越权访问返回 404 而非 403 ——
 * 后端刻意不区分「不存在」与「是别人的」，避免拿一串 id 就能探测出哪些存在。
 * ==================================================================== */

/**
 * 我的收货地址列表。默认地址排最前，其余按新增倒序。
 *
 * 返回 AddressVO[]：{ addressId, consignee, phone, province, city, district,
 *                    detail, isDefault, fullAddress }
 * fullAddress 是服务端拼好的「省+市+区县+详细地址」，区县为空时自动跳过 ——
 * 前端直接展示它即可，不要再自己拼一遍（否则「区县为空不留空格」这条规则会出现两份）。
 */
export function getAddressList() {
  return request.get('/address/list')
}

/**
 * 新增收货地址。
 *
 * 名下第一条地址会被后端自动置为默认（即使没传 isDefault=1）——
 * 这样就不会出现「有地址但没有默认地址」，结算页不必再写一层兜底。
 *
 * @returns {Promise<number>} 新地址主键，可据此把它设为选中项
 */
export function createAddress(payload) {
  return request.post('/address', payload)
}

/**
 * 编辑收货地址。语义是**整体覆盖**（不是部分更新）：除 district 外所有字段都必填，
 * 提交的就是表单里的完整值。
 *
 * 注意：已经是默认地址的记录，不能靠「编辑时不勾默认」把它降级 ——
 * 后端会保留其默认身份。取消默认的唯一途径是把别的地址设为默认，或删掉这一条。
 */
export function updateAddress(addressId, payload) {
  return request.put(`/address/${addressId}`, payload)
}

/**
 * 删除收货地址。
 * 删掉的若是默认地址且还有其它地址，后端会把剩余最新的一条自动顶为默认。
 */
export function removeAddress(addressId) {
  return request.delete(`/address/${addressId}`)
}

/** 设为默认地址（同用户名下先清后置，保证最多一条默认）。 */
export function setDefaultAddress(addressId) {
  return request.put(`/address/${addressId}/default`)
}
