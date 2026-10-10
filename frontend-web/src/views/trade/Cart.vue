<template>
  <div class="cart-page">
    <div class="page-head">
      <div>
        <h2>购物车</h2>
        <p>调整购买数量、移除条目，确认后逐条生成订单</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadCart">刷新</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !items.length" animated :rows="5" />

    <!-- 接口不可用：行内错误态 + 重试。toast 已由 request.js 统一弹过，不重复弹 -->
    <el-alert
      v-else-if="errorState"
      type="error"
      show-icon
      :closable="false"
      title="购物车加载失败"
      description="无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试。"
      class="error-alert"
    >
      <template #default>
        <div class="error-actions">
          <el-button type="danger" size="small" @click="loadCart">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <template v-else-if="items.length">
      <el-card shadow="never" class="cart-card">
        <div class="cart-head">
          <span class="col-product">商品</span>
          <span class="col-price">单价</span>
          <span class="col-qty">数量</span>
          <span class="col-total">小计</span>
          <span class="col-op">操作</span>
        </div>

        <div v-for="item in items" :key="item.cartId" class="cart-row">
          <div class="col-product product-cell">
            <div class="thumb" :class="coverClass(item)" :style="{ background: productFallbackBg({ productId: item.productId }) }">
              <span class="thumb-fallback">{{ productFallbackText({ productName: item.productName }) }}</span>
              <img :src="productImage({ productId: item.productId, productName: item.productName, imageUrl: item.imageUrl })" :alt="item.productName" @error="onImgError" />
            </div>
            <div class="pinfo">
              <h4>{{ item.productName || `商品 #${item.productId}` }}</h4>
              <small>
                库存 {{ item.stock ?? 0 }} {{ item.unit || '件' }}
                <el-tag v-if="item.status !== 1" type="info" size="small" effect="plain">已下架</el-tag>
              </small>
            </div>
          </div>

          <div class="col-price price-cell">
            ¥{{ formatPrice(item.price) }}<span class="unit">/{{ item.unit || '件' }}</span>
          </div>

          <div class="col-qty">
            <el-input-number
              :model-value="item.quantity"
              :min="1"
              :max="maxQty(item)"
              size="small"
              :disabled="item.status !== 1"
              @change="(v) => changeQuantity(item, v)"
            />
            <small v-if="item.status !== 1" class="off-tip">商品已下架</small>
          </div>

          <div class="col-total total-cell">¥{{ formatPrice(item.totalAmount) }}</div>

          <div class="col-op">
            <el-button link type="danger" @click="removeItem(item)">删除</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="settle-card">
        <div class="settle-left">
          <span>共 <strong>{{ items.length }}</strong> 种商品，合计</span>
          <span class="settle-amount">¥{{ formatPrice(totalAmount) }}</span>
        </div>
        <div class="settle-right">
          <el-button :disabled="loading" @click="loadCart">重新计价</el-button>
          <el-button
            type="primary"
            size="large"
            :disabled="!settleableItems.length"
            @click="goCheckout"
          >
            去结算{{ settleableItems.length ? `（${settleableItems.length}）` : '' }}
          </el-button>
        </div>
      </el-card>

      <el-alert
        v-if="!settleableItems.length"
        type="warning"
        show-icon
        :closable="false"
        title="购物车内商品均已下架，无法结算"
        class="settle-alert"
      />
    </template>

    <el-card v-else shadow="never">
      <el-empty description="购物车还是空的">
        <el-button type="primary" @click="goProductList">去挑选商品</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCartList, removeCartItem, updateCartQuantity } from '@/api/trade'
import { productImage, productFallbackBg, productFallbackText } from '@/utils/imagery'

/**
 * 页面用途：我的购物车（采购方视角，身份取自登录态）。
 * 接口文件：frontend-web/src/api/trade.js
 * 主要接口：
 *   GET    /api/trade/cart/list          查询我的购物车
 *   PUT    /api/trade/cart/{cartId}      改数量（quantity=0 即删除）
 *   DELETE /api/trade/cart/{cartId}      删除条目
 *
 * 后端返回 MyBatis-Plus Page，records 元素为 CartVO：
 * cartId、productId、productName、imageUrl、unit、price、quantity、
 * totalAmount、stock、status、createTime。
 * status=0 表示商品已下架，前端需置灰并排除出结算。
 *
 * 结算说明（2026-10-10 改）：结算动作不再在本页完成，改为跳转到
 * trade/Checkout.vue（/trade/checkout）—— 因为下单现在必须带 addressId，
 * 得有个地方选收货地址、核对明细，弹窗里塞不下地址簿 + 商品清单两块。
 *
 * ⚠️ 这里曾经是「ElMessageBox 确认后直接循环调 createOrder」。那段代码本身就是坏的：
 *    `const { createOrder } = await import('@/api/trade')` 拿到的是 undefined
 *    （api/trade.js 当时没有导出 createOrder），调用抛 TypeError 后被循环里的
 *    catch 吞掉，最终提示成「N 笔下单全部失败，请检查后端服务与商品库存」——
 *    把前端自己的 bug 报成了后端故障。现在 createOrder 已补上，
 *    并统一由结算页调用，本页不再持有下单逻辑。
 *
 * 失败处理：所有写操作失败都弹提示，且不静默刷新列表（避免用户误以为改动成功）。
 */
const router = useRouter()

const loading = ref(false)
const items = ref([])
/** 接口失败标记：与「购物车是空的」区分开 */
const errorState = ref(false)

/** 可结算条目：商品在架且库存 > 0 */
const settleableItems = computed(() =>
  items.value.filter((i) => i.status === 1 && Number(i.stock || 0) > 0)
)

const totalAmount = computed(() =>
  settleableItems.value.reduce((sum, i) => sum + Number(i.totalAmount || 0), 0)
)

/** 图片 404 时隐藏 img，露出下层首字 */
function onImgError(e) { e.target.style.opacity = '0' }

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/** 数量上限：不超过剩余库存 */
function maxQty(item) {
  const stock = Number(item.stock || 0)
  return stock > 0 ? stock : 1
}

function coverClass(item) {
  const h = String(item.productName || item.productId || '').split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  return `t-${h % 6}`
}

async function loadCart() {
  loading.value = true
  errorState.value = false
  try {
    const data = await getCartList(1, 50)
    items.value = data?.records || []
    if (!items.value.length) {
      ElMessage.info('购物车是空的')
    }
  } catch (err) {
    items.value = []
    errorState.value = true
    console.error('购物车加载失败：', err)
  } finally {
    loading.value = false
  }
}

async function changeQuantity(item, value) {
  const next = Number(value)
  if (!Number.isFinite(next) || next < 1) {
    ElMessage.warning('购买数量至少为 1')
    await loadCart()
    return
  }
  if (next > Number(item.stock || 0)) {
    ElMessage.warning(`库存仅剩 ${item.stock} ${item.unit || '件'}`)
    await loadCart()
    return
  }
  if (next === Number(item.quantity)) return

  try {
    await updateCartQuantity(item.cartId, next)
    ElMessage.success('数量已更新')
    await loadCart()
  } catch (err) {
    ElMessage.error('修改数量失败，条目未变更')
    console.error('修改购物车数量失败：', err)
    await loadCart()
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm(
      `确定从购物车移除「${item.productName || '该商品'}」吗？`,
      '移除商品',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '再想想' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await removeCartItem(item.cartId)
    ElMessage.success('已移除')
    await loadCart()
  } catch (err) {
    ElMessage.error('移除失败，请稍后重试')
    console.error('移除购物车条目失败：', err)
  }
}

/**
 * 去结算：跳到结算确认页。
 *
 * 本页不再做任何校验/下单 —— 结算页会自己按「在架且库存 > 0」重新取一遍购物车，
 * 所以即便用户在这页停留久了商品被下架，结算页也能如实反映。
 * 这里只挡一下「一件都不能结算」的空转入，避免跳到结算页看到空态。
 */
function goCheckout() {
  if (!settleableItems.value.length) return
  router.push('/trade/checkout')
}

function goProductList() {
  router.push('/trade/products')
}

onMounted(loadCart)
</script>

<style scoped>
.cart-page { color: var(--el-text-color-primary); }


.cart-card { border-radius: 14px; overflow: hidden; }

.cart-head,
.cart-row {
  display: grid;
  grid-template-columns: 1fr 130px 150px 130px 70px;
  gap: 12px;
  align-items: center;
}
.cart-head {
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  color: #909399;
}
.cart-row {
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.cart-row:last-child { border-bottom: none; }

.col-product { display: flex; align-items: center; gap: 12px; min-width: 0; }

.thumb {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(20, 48, 43, 0.35);
  font-size: 18px;
  overflow: hidden;
}
.thumb .thumb-fallback { position: absolute; z-index: 0; }
.thumb img { position: relative; z-index: 1; width: 100%; height: 100%; object-fit: cover; }
.t-0 { background: linear-gradient(135deg, #2d7a4c, #73b586); }
.t-1 { background: linear-gradient(135deg, #91602a, #d4a164); }
.t-2 { background: linear-gradient(135deg, #2c6679, #78adba); }
.t-3 { background: linear-gradient(135deg, #674b7d, #a991ba); }
.t-4 { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.t-5 { background: linear-gradient(135deg, #2f6b6e, #7cb5b0); }

.pinfo { min-width: 0; }
.pinfo h4 {
  margin: 0 0 6px;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pinfo small {
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.price-cell { color: #606266; font-size: 14px; }
.price-cell .unit { font-size: 12px; color: #909399; }

.col-qty { display: flex; flex-direction: column; gap: 4px; }
.off-tip { color: #e6a23c; font-size: 12px; }

.total-cell { color: #f56c6c; font-weight: 700; font-size: 15px; }

.col-op { text-align: right; }

.settle-card {
  margin-top: 16px;
  border-radius: 14px;
}
.settle-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}
.settle-left { display: flex; align-items: baseline; gap: 8px; }
.settle-left strong { color: var(--el-text-color-primary); }
.settle-amount {
  font-size: 24px;
  font-weight: 700;
  color: #f56c6c;
}
.settle-right { display: flex; gap: 10px; }

.settle-alert { margin-top: 12px; }

.error-alert { margin-top: 4px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { margin-top: 8px; }

@media (max-width: 860px) {
  .cart-head { display: none; }
  .cart-row { grid-template-columns: 1fr; gap: 8px; }
  .col-op { text-align: left; }
}
</style>
