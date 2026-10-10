<template>
  <div class="checkout-page">
    <div class="page-head">
      <div>
        <h2>确认订单</h2>
        <p>选择收货地址、核对商品与数量后提交。提交后订单为「待支付」，货款与发货由买卖双方在订单列表里继续流转。</p>
      </div>
      <div class="head-actions">
        <el-button @click="goCart">返回购物车</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" animated :rows="6" />

    <!-- 没有可结算商品：多来自「直接输 URL 进来」或「下单成功后返回上一页」 -->
    <el-card v-else-if="!settleable.length" shadow="never" class="content-card">
      <el-empty description="购物车里没有可结算的商品">
        <el-button type="primary" @click="goProductList">去挑选商品</el-button>
        <el-button @click="goOrders">查看我的订单</el-button>
      </el-empty>
    </el-card>

    <div v-else class="co-body">
      <div class="co-main">
        <!-- ============ 收货地址 ============ -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-title">
              <span>收货地址</span>
              <div class="card-tools">
                <el-button link type="primary" @click="openAddressForm(null)">+ 新增地址</el-button>
                <el-button link type="primary" @click="goAddress">管理地址</el-button>
              </div>
            </div>
          </template>

          <el-skeleton v-if="loadingAddress" animated :rows="2" />

          <!--
            地址用整块可点的卡片而不是 el-radio：
            收货人、电话、完整地址三行信息都要能一眼扫到，
            单选按钮那种「一行文字 + 一个圈」的形态放不下，
            而结算页选错地址的代价是货发错地方。
          -->
          <div v-else-if="addresses.length" class="addr-grid">
            <button
              v-for="a in addresses"
              :key="a.addressId"
              type="button"
              class="addr-card"
              :class="{ 'is-on': selectedId === a.addressId }"
              @click="selectedId = a.addressId"
            >
              <span class="ac-check" aria-hidden="true"></span>
              <span class="ac-top">
                <strong>{{ a.consignee }}</strong>
                <span class="ac-phone">{{ a.phone }}</span>
                <el-tag v-if="a.isDefault === 1" size="small" effect="plain" class="ac-tag">默认</el-tag>
              </span>
              <span class="ac-addr">{{ a.fullAddress }}</span>
            </button>
          </div>

          <el-alert
            v-else
            type="info"
            show-icon
            :closable="false"
            title="还没有收货地址"
            description="订单提交后要进入农户发货环节，没有收货人的订单农户处理不了，所以请先新增一条收货地址。"
          />
        </el-card>

        <!-- ============ 商品明细 ============ -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-title">
              <span>商品明细</span>
              <span class="card-count">共 {{ settleable.length }} 种商品</span>
            </div>
          </template>

          <div class="gd-head">
            <span class="col-goods">商品</span>
            <span class="col-price">单价</span>
            <span class="col-qty">数量</span>
            <span class="col-total">小计</span>
          </div>

          <div v-for="item in settleable" :key="item.cartId" class="gd-row">
            <div class="col-goods goods-cell">
              <div
                class="thumb"
                :class="coverClass(item)"
                :style="{ background: productFallbackBg({ productId: item.productId }) }"
              >
                <span class="thumb-fallback">{{ productFallbackText({ productName: item.productName }) }}</span>
                <img
                  :src="productImage({ productId: item.productId, productName: item.productName, imageUrl: item.imageUrl })"
                  :alt="item.productName"
                  @error="onImgError"
                />
              </div>
              <div class="pinfo">
                <h4>{{ item.productName || `商品 #${item.productId}` }}</h4>
                <small>库存 {{ item.stock ?? 0 }} {{ item.unit || '件' }}</small>
              </div>
            </div>
            <div class="col-price">¥{{ formatPrice(item.price) }} <span class="unit">/{{ item.unit || '件' }}</span></div>
            <div class="col-qty">{{ item.quantity }} {{ item.unit || '件' }}</div>
            <div class="col-total total-cell">¥{{ formatPrice(item.totalAmount) }}</div>
          </div>
        </el-card>

        <!-- ============ 备注 ============ -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-title">
              <span>订单备注</span>
              <span class="card-count">选填</span>
            </div>
          </template>
          <el-input
            v-model="remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="如「工作日送达，请提前电话联系」「按 20 斤一箱装」"
          />
          <p class="remark-tip">
            这批 {{ settleable.length }} 种商品会<b>各自生成一笔订单</b>，备注会写到每一笔上。
          </p>
        </el-card>
      </div>

      <!-- ============ 右侧汇总 ============ -->
      <aside class="co-side">
        <el-card shadow="never" class="content-card sum-card">
          <h3>订单金额</h3>
          <div class="sum-row"><span>商品金额</span><span>¥{{ formatPrice(totalAmount) }}</span></div>
          <div class="sum-row"><span>商品件数</span><span>{{ totalQuantity }} 件</span></div>
          <!-- 运费当前恒为 0：t_order 里没有运费字段，农产品交易暂不参与计费。
               这里显式列出来而不是省略，是为了让用户明确知道「不收运费」，
               而不是「不知道收不收」。 -->
          <div class="sum-row"><span>运费</span><span>¥0.00</span></div>
          <div class="sum-total">
            <span>应付合计</span>
            <strong>¥{{ formatPrice(totalAmount) }}</strong>
          </div>

          <el-button
            type="primary"
            size="large"
            class="sum-btn"
            :loading="submitting"
            :disabled="!selectedId"
            @click="submit"
          >
            提交订单
          </el-button>

          <p v-if="!selectedId" class="sum-tip is-warn">请先选择收货地址</p>
          <p v-else class="sum-tip">提交后为「待支付」，在「我的订单」里付款</p>
        </el-card>
      </aside>
    </div>

    <AddressFormDialog v-model="addrVisible" :address="editingAddress" @saved="onAddressSaved" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, getAddressList, getCartList } from '@/api/trade'
import { productImage, productFallbackBg, productFallbackText } from '@/utils/imagery'
import AddressFormDialog from '@/components/AddressFormDialog.vue'

/**
 * 页面用途：结算确认（采购方视角）。购物车点「结算」跳到这里。
 *
 * 接口文件：frontend-web/src/api/trade.js
 *   GET  /api/trade/cart/list                取购物车（结算清单的来源）
 *   GET  /api/address/list                   取收货地址
 *   POST /api/trade/order                    下单（逐条，一次一种商品）
 *
 * ★ 为什么要有这一页，而不是在购物车里直接弹个确认框：
 *   原来的做法是购物车点「结算」→ ElMessageBox 确认 → 直接循环下单。
 *   那条路径上**没有收货地址**（订单表当时也没有这几个字段），
 *   于是「货发到哪」在系统里无处可答。现在下单必须带 addressId，
 *   收货地址就得有个地方选，确认订单也得有个页面把选中的商品完整列出来
 *   —— 弹窗里塞不下地址簿 + 商品明细这两块。
 *
 * ★ 为什么一次结算会生成多笔订单：
 *   这是后端表结构决定的 —— t_order 一行一种商品（有 product_id 字段，
 *   没有订单明细子表），所以 N 种商品必然是 N 笔订单，无法合并成一笔。
 *   前端如实告知（提示语里写明「各自生成一笔订单」），
 *   不用「合并显示成一笔」的假象去掩盖。
 */

const router = useRouter()

const loading = ref(true)
const loadingAddress = ref(true)
const submitting = ref(false)
const addrVisible = ref(false)
const editingAddress = ref(null)

const items = ref([])
const addresses = ref([])
const selectedId = ref(null)
const remark = ref('')

/**
 * 可结算条目：商品在架且库存 > 0。
 *
 * 判据与 Cart.vue 的 settleableItems **必须完全一致** ——
 * 购物车底部写的「结算（N）」和这一页列出的商品数量如果不一致，
 * 用户会以为少买了东西。两处任何一处改口径，另一处要跟著改。
 */
const settleable = computed(() =>
  items.value.filter((i) => i.status === 1 && Number(i.stock || 0) > 0)
)

const totalAmount = computed(() =>
  settleable.value.reduce((sum, i) => sum + Number(i.totalAmount || 0), 0)
)

const totalQuantity = computed(() =>
  settleable.value.reduce((sum, i) => sum + Number(i.quantity || 0), 0)
)

onMounted(() => {
  loadCart()
  loadAddress()
})

async function loadCart() {
  loading.value = true
  try {
    const data = await getCartList(1, 100)
    items.value = data?.records || []
  } catch (err) {
    items.value = []
    console.error('结算页加载购物车失败：', err)
  } finally {
    loading.value = false
  }
}

async function loadAddress(preferId) {
  loadingAddress.value = true
  try {
    // 后端已按「默认地址在前」返回，所以第一个就是默认地址，不必在前端再排一遍
    addresses.value = (await getAddressList()) || []
    applyDefaultSelection(preferId)
  } catch (err) {
    addresses.value = []
    console.error('结算页加载收货地址失败：', err)
  } finally {
    loadingAddress.value = false
  }
}

/**
 * 决定选中哪条地址。
 *
 * @param {number} [preferId] 刚新增 / 刚编辑的那条，优先选中它 ——
 *        用户在结算页点「新增地址」填完，期望的是「就用这条下单」，
 *        而不是填完还得回列表里自己再点一下。
 */
function applyDefaultSelection(preferId) {
  const list = addresses.value
  if (!list.length) {
    selectedId.value = null
    return
  }
  if (preferId && list.some((a) => a.addressId === preferId)) {
    selectedId.value = preferId
    return
  }
  // 已是有效选择就不动（例如用户已手动点过另一条）
  if (selectedId.value && list.some((a) => a.addressId === selectedId.value)) {
    return
  }
  selectedId.value = list[0].addressId
}

function openAddressForm(address) {
  editingAddress.value = address
  addrVisible.value = true
}

async function onAddressSaved(addressId) {
  // 保存后重新拉列表并把这条设为选中：新增的那条不在本地数组里，
  // 只靠 push 会拿到一个缺少 fullAddress 的半成品对象（拼串是服务端做的）
  await loadAddress(addressId)
}

async function submit() {
  if (!selectedId.value) return

  // 不再弹二次确认框：这一页本身就是「确认订单」页，
  // 商品、地址、金额都在眼前，再弹一层只是多点一次。
  // 但提交是不可逆动作，按钮给出 loading 且期间禁用。
  submitting.value = true

  // 逐条下单：中途失败要如实告知已成功多少单，不能让用户以为全成了而重复下单
  const succeeded = []
  const failed = []
  for (const item of settleable.value) {
    try {
      await createOrder({
        productId: item.productId,
        quantity: item.quantity,
        addressId: selectedId.value,
        orderRemark: remark.value || undefined
      })
      succeeded.push(item.productName)
    } catch (err) {
      // 单条失败的原因（库存不足 / 商品刚被下架 / 地址被删）由拦截器弹过 toast，
      // 这里只记录下来，最后统一给一句汇总，避免 N 个 toast 堆在屏幕上
      failed.push(item.productName || `商品 #${item.productId}`)
      console.error('下单失败：', item.productName, err)
    }
  }

  submitting.value = false

  if (!failed.length) {
    ElMessage.success(`已生成 ${succeeded.length} 笔订单，请在订单列表完成支付`)
    router.push('/trade/orders')
    return
  }

  // 部分成功：留在本页并重新拉购物车 —— 成功的那些后端已经从车里删掉了，
  // 重新拉一次，页面上就只剩没买成的那几件，用户可以直接重试，不会重复下单
  if (!succeeded.length) {
    ElMessage.error(`${failed.length} 笔下单全部失败，请检查商品库存与收货地址后重试`)
  } else {
    ElMessage.warning(
      `成功 ${succeeded.length} 笔，失败 ${failed.length} 笔（${failed.join('、')}），已从清单中移除成功的部分`
    )
  }
  await loadCart()
}

/** 图片 404 时隐藏 img，露出下层首字（与购物车/商品列表同一策略） */
function onImgError(e) {
  e.target.style.opacity = '0'
}

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/* 商品名首字哈希取底色：与 Cart.vue 的 coverClass 逐字一致，
   这样同一件货在购物车与结算页是同一个底色。
   实际渲染时这个 class 会被上面的内联 style（productFallbackBg，按 productId 取色）
   覆盖 —— 保留它只是为了「万一哪天去掉内联 style」仍有一层兜底，
   两页的兜底值保持一致才不会出现只在一侧生效的差异。 */
function coverClass(item) {
  const h = String(item.productName || item.productId || '').split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  return `t-${h % 6}`
}

function goCart() {
  router.push('/trade/cart')
}

function goOrders() {
  router.push('/trade/orders')
}

function goProductList() {
  router.push('/trade/products')
}

function goAddress() {
  router.push('/user/address')
}
</script>

<style scoped>
.checkout-page {
  color: var(--el-text-color-primary);
}

/* 左主区 + 右汇总栏。
   固定 300px 的右栏而不是 auto-fit 栅格：汇总卡里的金额要纵向对齐，
   宽度随视口浮动会让数字每一屏都跳一下。 */
.co-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}
.co-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}
/* 汇总卡吸顶：商品明细比它长得多，滚到下面时金额要还在视野里 */
.co-side {
  position: sticky;
  top: 88px;
}

.card-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* ===== 收货地址卡片 ===== */
.addr-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.addr-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 13px 15px 13px 34px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-md);
  background: var(--el-fill-color-blank);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.addr-card:hover {
  border-color: var(--el-color-primary-light-5);
}
.addr-card.is-on {
  border-color: var(--rxt-primary);
  background: var(--el-color-primary-light-9);
}
/* 选中圆点在左上角，用绝对定位不占文本行宽 */
.ac-check {
  position: absolute;
  left: 13px;
  top: 15px;
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  background: var(--el-fill-color-blank);
}
.addr-card.is-on .ac-check {
  border-color: var(--rxt-primary);
  background: var(--rxt-primary);
  /* 用内阴影画出中间的实心圆，省一层 DOM */
  box-shadow: inset 0 0 0 3px #fff;
}
.ac-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ac-top strong {
  font-size: 14px;
}
.ac-phone {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ac-tag {
  --el-tag-border-color: var(--rxt-primary);
  --el-tag-text-color: var(--rxt-primary);
}
.ac-addr {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

/* ===== 商品明细 ===== */
.gd-head,
.gd-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 90px 110px;
  align-items: center;
  gap: 12px;
}
.gd-head {
  padding: 0 0 10px;
  border-bottom: 1px solid var(--rxt-border);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.gd-row {
  padding: 12px 0;
  border-bottom: 1px dashed var(--rxt-border);
}
.gd-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.goods-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.thumb {
  position: relative;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  overflow: hidden;
}
.thumb-fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}
.thumb img {
  position: relative;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.pinfo h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}
.pinfo small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.col-price,
.col-total {
  text-align: right;
}
.unit {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.total-cell {
  color: var(--rxt-accent-text, #b26a00);
  font-weight: 600;
}

.remark-tip {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

/* ===== 右侧汇总 ===== */
.sum-card h3 {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
}
.sum-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.sum-row span:last-child {
  font-variant-numeric: tabular-nums;
}
.sum-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--rxt-border);
}
.sum-total strong {
  color: var(--rxt-accent);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.sum-btn {
  width: 100%;
  margin-top: 16px;
}
.sum-tip {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}
.sum-tip.is-warn {
  color: var(--el-color-danger);
}

/* 品牌图底：与购物车同一套取色（值直接从 Cart.vue 抄过来，保持一致） */
.t-0 { background: linear-gradient(135deg, #2d7a4c, #73b586); }
.t-1 { background: linear-gradient(135deg, #91602a, #d4a164); }
.t-2 { background: linear-gradient(135deg, #2c6679, #78adba); }
.t-3 { background: linear-gradient(135deg, #674b7d, #a991ba); }
.t-4 { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.t-5 { background: linear-gradient(135deg, #2f6b6e, #7cb5b0); }

/* 窄屏：汇总栏落到下面。
   业务端只做桌面端，这里只是避免窗口拖窄时右栏被压成一条。 */
@media (max-width: 1100px) {
  .co-body {
    grid-template-columns: minmax(0, 1fr);
  }
  .co-side {
    position: static;
  }
  .addr-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
