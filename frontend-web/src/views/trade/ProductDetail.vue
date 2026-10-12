<template>
  <div class="product-detail-page">
    <!-- 页面用途：商品详情（REQ-06）。公开页面，游客可浏览；加购与下单需要登录。 -->
    <div class="page-head">
      <el-button link @click="goBack">← 返回商品货源</el-button>
      <h2 v-if="product">{{ product.productName }}</h2>
    </div>

    <el-skeleton v-if="loading" animated :rows="8" />

    <!-- 接口失败：行内错误态 + 重试。toast 已由 request.js 统一弹过一次，这里不重复弹 -->
    <el-alert
      v-else-if="errorState"
      type="error"
      show-icon
      :closable="false"
      title="商品详情加载失败"
      class="error-alert"
    >
      <template #default>
        <div class="error-desc">{{ errorMessage }}</div>
        <div class="error-actions">
          <el-button type="danger" size="small" @click="load">重新加载</el-button>
          <el-button size="small" @click="goBack">返回列表</el-button>
        </div>
      </template>
    </el-alert>

    <template v-else-if="product">
      <el-row :gutter="22">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="gallery-card">
            <div class="main-image">
              <img v-if="currentImage" :src="currentImage" :alt="product.productName" />
              <span v-else class="image-fallback">{{ (product.productName || '农产').slice(0, 2) }}</span>
            </div>
            <div v-if="images.length > 1" class="thumbs">
              <button
                v-for="(image, index) in images"
                :key="`${image}-${index}`"
                type="button"
                class="thumb"
                :class="{ active: currentImage === image }"
                @click="currentImage = image"
              >
                <img :src="image" :alt="`${product.productName} 第 ${index + 1} 张`" />
              </button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="info-card">
            <div class="title-row">
              <h3>{{ product.productName }}</h3>
              <el-tag v-if="product.status !== 1" type="info" effect="plain">已下架</el-tag>
            </div>

            <div class="price-row">
              <span class="price">¥{{ formatPrice(product.price) }}</span>
              <span class="unit">/ {{ product.unit || '件' }}</span>
              <span class="stock">库存 {{ product.stock ?? 0 }} {{ product.unit || '件' }}</span>
            </div>

            <el-descriptions :column="2" border class="meta-table">
              <el-descriptions-item label="品类">{{ product.category || '—' }}</el-descriptions-item>
              <el-descriptions-item label="产地">{{ product.origin || '—' }}</el-descriptions-item>
              <el-descriptions-item label="上架时间">{{ formatDateTime(product.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="审核状态">{{ product.auditStatus || '—' }}</el-descriptions-item>
            </el-descriptions>

            <p class="description">{{ product.description || '卖家暂未填写商品描述' }}</p>

            <div class="buy-row">
              <el-input-number
                v-model="quantity"
                :min="1"
                :max="maxQuantity"
                :disabled="!canBuy"
                controls-position="right"
              />
              <el-button type="primary" :disabled="!canBuy" :loading="adding" @click="handleAddToCart">
                加入购物车
              </el-button>
              <el-button :disabled="!canBuy" @click="goCart">去购物车结算</el-button>
            </div>

            <el-alert
              v-if="product.status !== 1"
              class="buy-alert"
              type="warning"
              :closable="false"
              show-icon
              title="该商品已下架，无法下单"
            />
            <el-alert
              v-else-if="maxQuantity === 0"
              class="buy-alert"
              type="warning"
              :closable="false"
              show-icon
              title="该商品当前无库存"
            />
            <el-alert
              v-else-if="!loggedIn"
              class="buy-alert"
              type="info"
              :closable="false"
              show-icon
              title="登录后即可加入购物车"
              description="游客可以浏览商品与评价；加购、下单需要先登录或注册"
            />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="22">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="seller-card">
            <template #header><strong>发布农户</strong></template>
            <div class="seller-head">
              <el-avatar :size="54" :src="product.sellerAvatarUrl">
                {{ (product.sellerName || '农').slice(0, 1) }}
              </el-avatar>
              <div>
                <strong>{{ product.sellerName || '—' }}</strong>
                <small>{{ product.sellerMainProduct || '农产品经营' }}</small>
              </div>
            </div>
            <p class="seller-scale">{{ product.sellerScaleDesc || '该农户暂未填写经营规模' }}</p>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="comment-card">
            <template #header>
              <div class="card-title">
                <strong>商品评价</strong>
                <span>共 {{ product.commentTotal || 0 }} 条，此处展示最新 {{ comments.length }} 条（仅已过审）</span>
              </div>
            </template>

            <div v-if="comments.length" class="comment-list">
              <div v-for="comment in comments" :key="comment.commentId" class="comment-item">
                <el-avatar :size="34" :src="comment.avatarUrl">
                  {{ (comment.userName || '用').slice(0, 1) }}
                </el-avatar>
                <div class="comment-body">
                  <div class="comment-meta">
                    <strong>{{ comment.userName || '匿名用户' }}</strong>
                    <span>{{ formatDateTime(comment.createTime) }}</span>
                  </div>
                  <p>{{ comment.content }}</p>
                </div>
              </div>
            </div>
            <el-empty v-else description="还没有评价" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-card v-else shadow="never">
      <el-empty description="商品不存在或已下架">
        <el-button type="primary" @click="goBack">返回商品货源</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addToCart, getTradeProductDetail } from '@/api/trade'
import { getToken } from '@/utils/auth'
import { productImage } from '@/utils/imagery'

/**
 * 页面用途：商品详情（REQ-06）。
 * 接口文件：frontend-web/src/api/trade.js
 * 主要接口：GET /api/trade/products/{productId}
 *   → ProductDetailVO：商品全字段 + 发布农户公开信息 + 已过审评论首页。
 *
 * 该接口属于游客可访问的公开接口（PublicEndpoints 里只放行 GET），
 * 因此本页不需要登录即可渲染；「加入购物车」等写操作仍需登录。
 *
 * 失败处理：接口异常一律弹提示 + 行内错误态，不静默失败成空页面 ——
 * 「商品不存在（404）」和「拿不到数据（网络/后端未起）」在界面上必须能区分开。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorState = ref(false)
const errorMessage = ref('')
const product = ref(null)
const quantity = ref(1)
const adding = ref(false)
const currentImage = ref('')

const loggedIn = computed(() => Boolean(getToken()))

/** 图集：主图排第一，去重后展示。演示数据 imageUrl 全 NULL，所以主图走 productImage 兜底。 */
const images = computed(() => {
  const list = Array.isArray(product.value?.images)
    ? product.value.images.filter(Boolean)
    : []
  const main = productImage(product.value)
  if (main && !list.includes(main)) {
    return [main, ...list]
  }
  return list
})

/** 可购买上限：库存为 0 或非数值时最少按 1 显示，避免 el-input-number 的 max < min */
const maxQuantity = computed(() => Math.max(1, Number(product.value?.stock ?? 0)))

const comments = computed(() => product.value?.comments || [])

/** 只有「上架且有库存」才允许下单 */
const canBuy = computed(() =>
  Boolean(product.value) && product.value.status === 1 && maxQuantity.value > 0
)

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  const productId = route.params.id
  if (!productId) {
    product.value = null
    return
  }
  loading.value = true
  errorState.value = false
  try {
    const data = await getTradeProductDetail(productId)
    product.value = data || null
    quantity.value = 1
    // 主图优先，没有图集时也不留空字符串（空字符串会让 <img> 显示破图）
    currentImage.value = images.value[0] || ''
  } catch (err) {
    product.value = null
    errorState.value = true
    // request.js 已经把后端 message 弹过 toast（err.notified），这里只渲染行内错误态，
    // 因此不再弹第二次，避免同一次失败刷出两条提示
    errorMessage.value = err?.message || '无法连接后端服务，请确认后端已启动后重试'
  } finally {
    loading.value = false
  }
}

/** 游客点「加入购物车」时明确引导登录，而不是等接口返回 401 再弹一句「登录状态已失效」 */
function requireLogin() {
  if (loggedIn.value) {
    return true
  }
  ElMessage.warning('请先登录后再加入购物车')
  router.push('/login')
  return false
}

async function handleAddToCart() {
  if (!canBuy.value || !requireLogin()) {
    return
  }
  adding.value = true
  try {
    await addToCart(product.value.productId, quantity.value)
    ElMessage.success(`已加入购物车：${quantity.value} ${product.value.unit || '件'}`)
  } catch (err) {
    console.error('加入购物车失败：', err)
  } finally {
    adding.value = false
  }
}

function goCart() {
  if (!requireLogin()) {
    return
  }
  router.push('/trade/cart')
}

function goBack() {
  router.push('/trade/products')
}

onMounted(load)

// 从「相关商品」或直接改地址跳转到另一个商品时，组件会被复用（同一路由不同参数），
// 只靠 onMounted 不会重新加载，页面会停在上一件商品上
watch(() => route.params.id, (next, prev) => {
  if (next !== prev) {
    load()
  }
})
</script>

<style scoped>
.product-detail-page { color: var(--el-text-color-primary); }

.page-head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.page-head h2 { margin: 0; font-size: 22px; }

.gallery-card, .info-card, .seller-card, .comment-card { border-radius: 14px; margin-bottom: 18px; }

.main-image {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(135deg, #2d7a4c, #73b586);
}
.main-image img { width: 100%; height: 100%; object-fit: cover; }
.image-fallback { font-size: 46px; font-weight: 700; color: rgba(255, 255, 255, .85); letter-spacing: 3px; }

.thumbs { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.thumb {
  width: 60px; height: 60px; padding: 0; overflow: hidden;
  border: 1px solid var(--el-border-color); border-radius: 8px;
  background: transparent; cursor: pointer;
}
.thumb.active { border-color: var(--el-color-primary); box-shadow: 0 0 0 1px var(--el-color-primary-light-5); }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }

.title-row { display: flex; align-items: center; gap: 10px; }
.title-row h3 { margin: 0; font-size: 18px; line-height: 1.5; }

.price-row { display: flex; align-items: baseline; gap: 8px; margin: 12px 0 16px; }
.price-row .price { font-size: 28px; font-weight: 700; color: #f56c6c; }
.price-row .unit { font-size: 13px; color: #909399; }
.price-row .stock { margin-left: auto; font-size: 13px; color: #909399; }

.meta-table { margin-bottom: 14px; }

.description { margin: 0 0 16px; color: var(--el-text-color-secondary); line-height: 1.8; white-space: pre-wrap; }

.buy-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.buy-alert { margin-top: 14px; }

.seller-head { display: flex; align-items: center; gap: 13px; }
.seller-head strong, .seller-head small { display: block; }
.seller-head strong { font-size: 16px; }
.seller-head small { margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.seller-scale { margin: 14px 0 0; color: var(--el-text-color-secondary); line-height: 1.7; font-size: 13px; }

.card-title span { color: var(--el-text-color-secondary); font-size: 12px; }

.comment-list { display: grid; gap: 14px; }
.comment-item { display: flex; gap: 12px; padding-bottom: 14px; border-bottom: 1px dashed var(--el-border-color-lighter); }
.comment-item:last-child { padding-bottom: 0; border-bottom: 0; }
.comment-body { flex: 1; min-width: 0; }
.comment-meta { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.comment-meta strong { font-size: 14px; }
.comment-meta span { color: var(--el-text-color-secondary); font-size: 12px; }
.comment-body p { margin: 6px 0 0; line-height: 1.7; word-break: break-word; }

.error-alert { margin-top: 4px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { display: flex; gap: 10px; margin-top: 10px; }

@media (max-width: 680px) {
  .main-image { height: 220px; }
  .buy-row { flex-direction: column; align-items: stretch; }
}
</style>
