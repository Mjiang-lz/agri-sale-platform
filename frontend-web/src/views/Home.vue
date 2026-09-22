<template>
  <div class="home-page">
    <!-- 页面头部：参考 FinanceApply.vue 的 page-head 布局 -->
    <div class="page-head">
      <div>
        <h2>新闻公告</h2>
        <p>了解平台最新动态与农业行业新闻。</p>
      </div>
      <div class="head-tags">
        <el-tag type="success" effect="plain">接口：GET /api/home/news</el-tag>
      </div>
    </div>

    <!-- 轮播内容区 -->
    <el-card shadow="never" class="content-card">
      <!-- 加载骨架屏loading判断 -->
      <el-skeleton v-if="loading.news" animated :rows="5" />
      
      <!-- 轮播图 -->
      <el-carousel
        v-else-if="newsList.length"
        height="320px"
        :interval="4000"
        arrow="hover"
      >
        <el-carousel-item v-for="item in newsList" :key="item.id">
          <div class="banner" :style="{ backgroundImage: `url(${item.image})` }">
            <div class="banner-mask">
              <h3 class="banner-title">{{ item.title }}</h3>
              <p class="banner-summary">{{ item.summary }}</p>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>

      <!-- 空状态 -->
      <el-empty v-else description="暂无新闻公告" />
    </el-card>

    <!-- ============ 2. 金融产品展示 ============ -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>金融产品</strong>
          <el-link type="primary" :underline="false" @click="goFinanceList">
            查看更多
          </el-link>
        </div>
      </template>

      <!-- 加载中 -->
      <el-skeleton v-if="loading.product" animated :rows="4" />

      <!-- 有数据：卡片列表 -->
      <el-row v-else-if="productList.length" :gutter="16">
        <el-col
          v-for="p in productList"
          :key="p.id"
          :span = 24
        >
          <el-card shadow="hover" class="product-card">
            <!-- 顶部：产品名 + 银行标签 -->
            <div class="product-head">
              <span class="product-name">{{ p.name }}</span>
              <el-tag size="small" type="success" effect="plain">
                {{ p.bankName }}
              </el-tag>
            </div>

            <!-- 利率 -->
            <div class="product-rate">
              <span class="num">{{ p.rate }}</span>
              <span class="unit">%</span>
              <span class="label">年化利率</span>
            </div>

            <!-- 额度、期限 -->
            <ul class="product-meta">
              <li><span>额度：</span>{{ p.amountRange }}</li>
              <li><span>期限：</span>{{ p.termRange }}</li>
            </ul>

            <!-- 申请条件 -->
            <div class="product-condition">
              <span class="condition-label">申请条件：</span>{{ p.condition }}
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 空状态 -->
      <el-empty v-else description="暂无金融产品" />
    </el-card>

  </div>
</template>

<script setup>
import { ref, onMounted,reactive } from 'vue'

import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getHomeNews, getHomeProducts } from '@/api/home'


// ==================== 全局假数据开关 ====================
// 后端没写好之前，设为 true，直接用假数据测试页面
// 后端写好后，改成 false，就会自动去调用真实接口
const useMock = true

// 新闻列表数据
const newsList = ref([])

// 获取新闻数据
async function loadNews() {
  loading.news = true
  console.log('开始加载新闻')
  try {
    if (useMock) {
      // 模拟网络延迟
      await new Promise((resolve) => setTimeout(resolve, 500))
      newsList.value = [
        { 
          id: 1, 
          title: '“融销通”平台一期上线试运行公告', 
          summary: '平台进入模拟运行环境，农户可体验融资申请与农产品线上交易全流程。', 
          image: 'https://picsum.photos/800/400?random=1'
        },
        { 
          id: 2, 
          title: '关于联合贷款撮合规则调整的通知', 
          summary: '联合融资人匹配新增“经营品类”与“所在地区”权重，匹配结果更精准。', 
          image: 'http://h.hiphotos.baidu.com/image/pic/item/7c1ed21b0ef41bd5f2c2a9e953da81cb39db3d1d.jpg' 
        },
        { 
          id: 3, 
          title: '关于xx通知', 
          summary: '联合融资人匹配新增“经营品类”与“所在地区”权重，匹配结果更精准。', 
          image: 'http://e.hiphotos.baidu.com/image/pic/item/a1ec08fa513d2697e542494057fbb2fb4316d81e.jpg' 
        }

      ]
      return // 使用假数据后，直接 return，不往后端发请求
    }

    // 后面是调用真实接口的逻辑（保留）
    const data = await getHomeNews()
    const list = normalizePage(data)
    newsList.value = list.map((item) => ({
      id: item.id,
      title: item.title,
      summary: item.summary || item.description || item.intro || '',
      image: resolveImageUrl(item.imageUrl || item.coverUrl || item.picUrl || item.image || item.cover || '')
      }))
  } catch (error) {
    console.error('新闻加载失败：', error)
    ElMessage.error('新闻加载失败，请稍后重试')
    newsList.value = []
  } finally {
    loading.news = false
    console.log('新闻加载结束，loading.news =', loading.news)
  }
}

// ======金融区
const router = useRouter()

// 加载状态：按区块区分，互不影响
const loading = reactive({
  news: false,
  product: false
})
// 金融产品数据
const productList = ref([])
/**
 * 处理图片地址：
 * - 完整 URL 直接返回
 * - 相对路径原样返回（依赖 Vite 代理或同源部署）
 * - 其他情况拼接基础地址
 */
function resolveImageUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  if (url.startsWith('/')) return url
  const base = import.meta.env.VITE_IMAGE_BASE_URL || ''
  return `${base}/${url}`
}

/** 后端分页对象转数组 */
function normalizePage(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}


/* ==================== 2. 金融产品 ==================== */
async function loadProducts() {
  loading.product = true
  try {
    if (useMock) {
      // 模拟网络延迟
      await new Promise((resolve) => setTimeout(resolve, 500))
      productList.value = [
        { id: 1, name: '惠农联保贷', bankName: '合作银行A', rate: '4.35', amountRange: '1万 ~ 30万', termRange: '6 ~ 36 个月', condition: '农户实名认证通过，3 户以上组成联合贷款小组' },
        { id: 2, name: '助农经营贷', bankName: '合作银行B', rate: '4.90', amountRange: '5万 ~ 50万', termRange: '12 ~ 60 个月', condition: '有稳定农产品销售流水，经营主体审核通过' },
        { id: 3, name: '乡村振兴小额贷', bankName: '合作银行C', rate: '3.85', amountRange: '5千 ~ 10万', termRange: '3 ~ 24 个月', condition: '农户实名认证通过，无不良记录' }
      ]
      return // 使用假数据后，直接 return，不往后端发请求
    }

    // 后面是调用真实接口的逻辑（保留）
    const data = await getHomeProducts()
    const list = normalizePage(data)
    productList.value = list.map((item) => ({ 
      id: item.id || item.productId || item.loanProductId,
      name: item.name || item.productName || '',
      bankName: item.bankName || item.bank || '合作银行',
      // 利率：后端可能返回 0.045 或 "4.5"，统一格式化成 "4.50"
      rate: formatRate(item.rate ?? item.annualRate),
      // 额度：后端可能直接给区间字符串，也可能给 min/max
      amountRange:
        item.amountRange ||
        `${formatMoney(item.minAmount || 0)} ~ ${formatMoney(item.maxAmount || 0)} 元`,
      // 期限：同理
      termRange:
        item.termRange ||
        `${item.minTerm || 6} ~ ${item.termMonths || 36} 个月`,
      condition: item.condition || item.applyCondition || '详见产品说明'
     }))
  } catch (error) {
    console.error('金融产品加载失败：', error)
    ElMessage.error('金融产品加载失败，请稍后重试')
    productList.value = []
  } finally {
    loading.product = false
  }
}


/** 利率格式化：0.045 -> 4.50，4.5 -> 4.50 */
function formatRate(value) {
  const num = Number(value || 0)
  // 小于 1 认为是小数形式（0.045），乘 100
  const percent = num < 1 ? num * 100 : num
  return percent.toFixed(2)
}

/** 金额格式化：10000 -> 1万 */
function formatMoney(value) {
  const num = Number(value || 0)
  if (num >= 10000) {
    return `${(num / 10000).toFixed(num % 10000 === 0 ? 0 : 1)}万`
  }
  return `${num}`
}

/** 跳转到融资服务列表页（按你项目的实际路由调整） */
function goFinanceList() {
  router.push('/finance')
}

/* 组件挂载完毕再读数据*/
onMounted(() => {
  loadNews()
  loadProducts()
})

</script>

<style scoped>
/* 样式严格限制在 scoped 内，不污染其他页面 */
.home-page { 
  color: var(--el-text-color-primary); 
}

/* 头部样式完全参考 FinanceApply.vue */
.page-head { 
  display: flex; 
  justify-content: space-between; 
  align-items: flex-start; 
  gap: 20px; 
  margin-bottom: 18px; 
}
.page-head h2 { 
  margin: 0; 
  font-size: 24px; 
}
.page-head p { 
  margin: 8px 0 0; 
  color: var(--el-text-color-secondary); 
  line-height: 1.7; 
}
.head-tags { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
}

/* 卡片样式 */
.content-card { 
  border-radius: 14px; 
}

/* 轮播图内部样式 */
.banner {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: flex-end;
  border-radius: 6px;
  overflow: hidden;
}
.banner-mask {
  width: 100%;
  padding: 20px;
  box-sizing: border-box;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: #fff;
}
.banner-title {
  margin: 0 0 8px;
  font-size: 20px;
}
.banner-summary {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

/* ============ 卡片通用 ============ */
.content-card {
  border-radius: 14px;
}
.section-card {
  margin-top: 18px;
}
.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ============ 金融产品卡片 ============ */
.product-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: 0.2s;
}
.product-card:hover {
  border-color: var(--el-color-primary);
}
.product-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.product-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.product-rate {
  color: #f56c6c;
  margin-bottom: 12px;
  line-height: 1;
}
.product-rate .num {
  font-size: 26px;
  font-weight: 700;
}
.product-rate .unit {
  font-size: 14px;
  margin-left: 2px;
}
.product-rate .label {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.product-meta {
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: #606266;
}
.product-meta li {
  display: flex;
 justify-content: flex-start; /* ✅ 改成左对齐，内容就会挨在一起 */
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}
.product-meta li span {
  color: #909399;
}
.product-condition {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.condition-label {
  color: #606266;
}

</style>