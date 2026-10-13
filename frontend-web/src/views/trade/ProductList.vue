<template>
  <div class="product-list-page">
    <div class="page-head">
      <div>
        <h2>商品货源</h2>
        <p>平台在架的农产品货源，支持按品类、产地与价格区间筛选</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadProducts">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input
          v-model="query.keyword"
          placeholder="搜索商品名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.origin" placeholder="全部产地" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="o in originOptions" :key="o" :label="o" :value="o" />
        </el-select>
        <div class="price-range">
          <el-input-number
            v-model="query.minPrice"
            :min="0"
            :controls="false"
            placeholder="最低价"
            style="width: 100px"
          />
          <span class="price-sep">—</span>
          <el-input-number
            v-model="query.maxPrice"
            :min="0"
            :controls="false"
            placeholder="最高价"
            style="width: 100px"
          />
          <span class="price-unit">元</span>
        </div>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-skeleton v-if="loading && !products.length" animated :rows="6" />

    <!-- 接口不可用：行内错误态 + 重试按钮。
         toast 已由 request.js 拦截器统一弹过一次，这里不重复弹，避免同一次失败刷两条。 -->
    <el-alert
      v-else-if="errorState"
      type="error"
      show-icon
      :closable="false"
      title="商品货源加载失败"
      class="error-alert"
    >
      <template #default>
        <div class="error-desc">无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试</div>
        <div class="error-actions">
          <el-button type="danger" size="small" @click="loadProducts">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <template v-else-if="products.length">
      <el-row :gutter="16">
        <el-col v-for="p in products" :key="p.productId" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card shadow="hover" class="goods-card" @click="goDetail(p)">
            <div class="goods-cover" :class="coverClass(p.category)" :style="{ background: productFallbackBg(p) }">
              <span class="cover-text">{{ productFallbackText(p) }}</span>
              <img :src="productImage(p)" :alt="p.productName" loading="lazy" @error="onImgError" />
              <el-tag v-if="p.status !== 1" class="cover-tag" type="info" size="small">已下架</el-tag>
            </div>
            <div class="goods-body">
              <h4 :title="p.productName">{{ p.productName }}</h4>
              <div class="goods-price">
                <span class="num">¥{{ formatPrice(p.price) }}</span>
                <span class="unit">/ {{ p.unit || '件' }}</span>
              </div>
              <ul class="goods-meta">
                <li><span>品类：</span>{{ p.category || '—' }}</li>
                <li><span>产地：</span>{{ p.origin || '—' }}</li>
                <li><span>库存：</span>{{ p.stock ?? 0 }} {{ p.unit || '件' }}</li>
              </ul>
              <div class="goods-footer">
                <el-button type="primary" size="small" @click.stop="goDetail(p)">查看详情</el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[12, 24, 48]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadProducts"
          @size-change="handleSizeChange"
        />
      </div>
    </template>

    <el-card v-else shadow="never">
      <el-empty :description="emptyText">
        <el-button v-if="hasFilter" type="primary" @click="resetQuery">清空筛选条件</el-button>
      </el-empty>
    </el-card>

    <!-- 返回顶部按钮。样式与参数统一由 components/PageBackTop.vue 提供，各页不再各写一份。 -->
    <PageBackTop />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTradeProducts } from '@/api/trade'
import { productImage, productFallbackBg, productFallbackText } from '@/utils/imagery'
import PageBackTop from '@/components/PageBackTop.vue'

/**
 * 页面用途：商品货源列表（公开接口，游客可访问）。
 * 接口文件：frontend-web/src/api/trade.js
 * 主要接口：GET /api/trade/products?pageNum=&pageSize=&keyword=&category=&minPrice=&maxPrice=&origin=
 *
 * 后端返回 MyBatis-Plus Page：
 * { current, pages, records, total, size }，records 元素为 ProductVO：
 * productId、productName、category、price、stock、unit、imageUrl、origin、
 * sellerId、auditStatus、status。
 *
 * 失败处理：接口异常一律弹提示，不静默失败成空列表 ——
 * 「没有商品」和「拿不到商品」在页面上必须能区分开。
 */
const router = useRouter()
const route = useRoute()

const loading = ref(false)
const products = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
/** 接口失败标记：与「没有商品」区分开，空态文案才不会误导 */
const errorState = ref(false)

const query = reactive({
  keyword: '',
  category: '',
  origin: '',
  minPrice: null,
  maxPrice: null
})

// 分类 / 产地下拉：后端没有字典接口，这里用一份已知全集兜底。
// 后端新增品类后需同步这里，否则新数据在下拉里选不到（仍会出现在「全部」里）。
const categoryOptions = ['蔬菜', '水果', '粮油', '干货', '水产', '畜禽产品']
const originOptions = ['江西赣州', '湖北荆州', '黑龙江五常']

const hasFilter = computed(() =>
  Boolean(query.keyword || query.category || query.origin ||
    query.minPrice !== null || query.maxPrice !== null)
)

const emptyText = computed(() =>
  hasFilter.value ? '没有符合条件的商品，试试放宽筛选条件' : '暂无在架商品'
)

/** 图片 404 时隐藏 img，露出下层的首字底色，避免碎图 */
function onImgError(e) {
  e.target.style.opacity = '0'
}

/** 价格格式化：后端 BigDecimal 序列化成 number，这里兜住非数值情况 */
function formatPrice(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

/** 封面配色：按品类稳定取色，同品类颜色一致 */
function coverClass(categoryName) {
  const palette = ['g-green', 'g-orange', 'g-blue', 'g-purple', 'g-brown', 'g-cyan']
  const idx = categoryOptions.indexOf(categoryName)
  if (idx >= 0) return palette[idx % palette.length]
  // 未知品类用名称做简易哈希，避免新品类全撞同一个色
  const h = String(categoryName || '').split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  return palette[h % palette.length]
}

async function loadProducts() {
  loading.value = true
  errorState.value = false
  try {
    const data = await getTradeProducts({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: query.keyword,
      category: query.category,
      origin: query.origin,
      minPrice: query.minPrice,
      maxPrice: query.maxPrice
    })
    const records = data?.records || []
    products.value = records
    total.value = Number(data?.total ?? records.length)
    if (!records.length) {
      ElMessage.info(hasFilter.value ? '没有符合条件的商品' : '暂无在架商品')
    }
  } catch (err) {
    products.value = []
    total.value = 0
    errorState.value = true
    // toast 已由 request.js 统一弹过（err.notified === true），这里只渲染行内错误态
    console.error('商品货源加载失败：', err)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadProducts()
}

function resetQuery() {
  query.keyword = ''
  query.category = ''
  query.origin = ''
  query.minPrice = null
  query.maxPrice = null
  handleSearch()
}

function handleSizeChange() {
  pageNum.value = 1
  loadProducts()
}

function goDetail(p) {
  router.push(`/trade/product/${p.productId}`)
}

/**
 * ★ 2026-10-10 修复：本页原先**从不读 route.query**。
 *
 * 上游其实都正确地传了参数 —— 首页搜索框 `push({ query: { keyword } })`、
 * 品类圆形入口 `goCategory()` `push({ query: { category } })` —— 但本页只有
 * 一个自己的 `query` 对象，初值全空，于是表现成「搜什么都只出全量列表」。
 * 看起来像搜索坏了，实际是参数在这一跳被丢掉了。
 *
 * `pick` 把非字符串（数组 / null / undefined）挡掉：query 在地址栏里用户可手改，
 * `?keyword=a&keyword=b` 会被解析成数组，直接塞进 el-input 会渲染成 "a,b"。
 */
function syncFromRoute() {
  const pick = (v) => (typeof v === 'string' ? v : '')
  query.keyword = pick(route.query.keyword)
  query.category = pick(route.query.category)
  query.origin = pick(route.query.origin)
}

// 同一个组件实例里再次从首页跳进来时 onMounted 不会再跑，所以必须监听路由。
watch(
  () => route.query,
  () => {
    syncFromRoute()
    pageNum.value = 1
    loadProducts()
  }
)

onMounted(() => {
  syncFromRoute()
  loadProducts()
})
</script>

<style scoped>
.product-list-page { color: var(--el-text-color-primary); }


.filter-card { margin-bottom: 18px; border-radius: 14px; }
.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.price-range { display: flex; align-items: center; gap: 8px; }
.price-sep  { color: var(--el-text-color-secondary); }
.price-unit { color: var(--el-text-color-secondary); font-size: 13px; }

.goods-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: 0.2s;
  overflow: hidden;
}
.goods-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 26px rgba(29, 70, 46, .1);
}

.goods-cover {
  position: relative;
  height: 132px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.goods-cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-text {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 34px;
  font-weight: 700;
  color: rgba(20, 48, 43, 0.22);
  letter-spacing: 2px;
  z-index: 0;
}
.goods-cover img { position: relative; z-index: 1; width: 100%; height: 100%; object-fit: cover; }
.cover-tag { position: absolute; top: 8px; right: 8px; }

.g-green  { background: linear-gradient(135deg, #2d7a4c, #73b586); }
.g-orange { background: linear-gradient(135deg, #91602a, #d4a164); }
.g-blue   { background: linear-gradient(135deg, #2c6679, #78adba); }
.g-purple { background: linear-gradient(135deg, #674b7d, #a991ba); }
.g-brown  { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.g-cyan   { background: linear-gradient(135deg, #2f6b6e, #7cb5b0); }

.goods-body { padding: 14px; }
.goods-body h4 {
  margin: 0 0 10px;
  font-size: 15px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.goods-price { margin-bottom: 10px; line-height: 1; }
.goods-price .num  { font-size: 22px; font-weight: 700; color: #f56c6c; }
.goods-price .unit { font-size: 12px; color: #909399; margin-left: 4px; }

.goods-meta {
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: #606266;
}
.goods-meta li {
  display: flex;
  padding: 3px 0;
  border-bottom: 1px dashed #ebeef5;
}
.goods-meta li span { color: #909399; margin-right: 6px; }

.goods-footer { display: flex; justify-content: flex-end; }


.error-alert { margin-top: 4px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { margin-top: 8px; }

@media (max-width: 680px) {
  .filter-row { flex-direction: column; align-items: stretch; }
  .price-range { justify-content: space-between; }
}
</style>
