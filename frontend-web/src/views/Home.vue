<template>
  <div class="home-page">

    <!-- 顶部统一说明：6 项子功能来源 M2 首页；详见评审 MR #1。 -->
    <div class="page-head">
      <div>
        <h2>融销通 · 首页</h2>
        <p>按需聚合新闻、金融产品、专家、农业知识与电商入口，逐项点击可进入详情。</p>
      </div>
      <div class="head-tags">
        <el-tag v-if="anyDemo" type="warning" effect="dark">演示数据</el-tag>
      </div>
    </div>

    <!-- ===========================================================
         区块 1 · 新闻轮播展示
         需求：按排序权重轮播；支持手动切换；点击跳转详情
         数据源：GET /api/home/news（后端尚未实现；本 MR 用前端演示数据填充，
                跳转统一走 /expert/knowledge 知识列表页，等新闻接口就绪后再切真接口）。
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>新闻公告</strong>
          <el-link type="primary" :underline="'never'" @click="goKnowledge">查看更多</el-link>
        </div>
      </template>

      <el-skeleton v-if="loading.news" animated :rows="5" />
      <el-carousel
        v-else-if="newsList.length"
        height="280px"
        :interval="4000"
        arrow="hover"
        indicator-position="outside"
        @click.stop="openNews(newsList[activeNewsIndex])"
      >
        <el-carousel-item v-for="(item, idx) in newsList" :key="item.id" @click="openNews(item)">
          <div class="banner" :style="{ backgroundImage: `url(${item.image})` }">
            <div class="banner-mask">
              <span class="banner-meta">置顶 · 排序权重 {{ item.weight }}</span>
              <h3 class="banner-title">{{ item.title }}</h3>
              <p class="banner-summary">{{ item.summary }}</p>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
      <el-empty v-else description="暂无新闻公告" />
    </el-card>

    <!-- ===========================================================
         区块 2 · 金融产品展示
         需求：读取产品列表并展示利率、额度、期限与申请条件
         数据源：GET /api/finance/loan-products
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>金融产品</strong>
          <el-link type="primary" :underline="'never'" @click="goFinanceList">查看更多</el-link>
        </div>
      </template>

      <el-skeleton v-if="loading.product" animated :rows="4" />
      <el-row v-else-if="productList.length" :gutter="16">
        <el-col v-for="p in productList" :key="p.id" :span="8">
          <el-card shadow="hover" class="product-card" @click="goFinanceList">
            <div class="product-head">
              <span class="product-name">{{ p.name }}</span>
              <el-tag size="small" :type="p.status === 1 ? 'success' : 'info'" effect="plain">
                {{ p.status === 1 ? '在售' : '已下架' }}
              </el-tag>
            </div>
            <div class="product-rate">
              <span class="num">{{ p.rate }}</span>
              <span class="unit">%</span>
              <span class="label">年化利率</span>
            </div>
            <ul class="product-meta">
              <li><span>额度：</span>{{ p.amountRange }}</li>
              <li><span>期限：</span>{{ p.termRange }}</li>
            </ul>
            <div class="product-condition">
              <span class="condition-label">申请条件：</span>{{ p.condition }}
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无金融产品" />
    </el-card>

    <!-- ===========================================================
         区块 3 · 专家一览
         需求：分页展示，支持按擅长领域筛选
         数据源：GET /api/expert/experts?domain=xxx
                → Result<List<ExpertVO>>，姓名/头像已在服务端装配好，无需二次请求
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>专家一览</strong>
          <div class="card-tools">
            <el-select v-model="expertDomain" placeholder="全部领域" clearable style="width: 160px" @change="loadExperts">
              <el-option v-for="d in expertDomains" :key="d" :label="d" :value="d" />
            </el-select>
          </div>
        </div>
      </template>

      <el-skeleton v-if="loading.expert" animated :rows="3" />
      <el-row v-else-if="expertList.length" :gutter="16">
        <el-col v-for="e in previewExperts" :key="e.expertId" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card shadow="hover" class="expert-card" @click="goExpertDetail(e)">
            <div class="expert-head">
              <span class="ua" :style="uaStyle(48)">
                <img v-if="e.avatarUrl && !uaBroken[e.userId]" :src="e.avatarUrl" :alt="e.realName" @error="uaBroken[e.userId] = true" />
                <span v-else class="ua-initial">{{ (e.realName || '专').slice(0, 1) }}</span>
              </span>
              <div class="expert-id">
                <strong>{{ e.realName || '农技专家' }}</strong>
                <el-tag size="small" effect="plain">{{ e.expertDomain }}</el-tag>
              </div>
            </div>
            <p class="expert-intro">{{ summary(e.introduction, 60) || '—' }}</p>
            <div class="expert-footer">
              <small v-if="e.workloadStat">{{ e.workloadStat }}</small>
              <el-button link type="primary" @click="goExpertDetail(e)">发起咨询</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无专家数据" />

    </el-card>

    <!-- ===========================================================
         区块 4 · 专家知识展示（按发布时间倒序；authorId 是专家主键，需落在专家列表内）
         数据源：GET /api/home/knowledge?limit=10
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>专家知识</strong>
          <el-link type="primary" :underline="'never'" @click="goKnowledge">查看更多</el-link>
        </div>
      </template>

      <el-skeleton v-if="loading.expertKnowledge" animated :rows="3" />
      <div v-else-if="expertKnowledgeList.length" class="knowledge-row">
        <article v-for="k in expertKnowledgeList" :key="k.knowledgeId" class="knowledge-mini" @click="openKnowledge(k)">
          <div class="kmeta">
            <el-tag size="small" effect="plain">{{ k.category }}</el-tag>
            <span>{{ formatDateTime(k.createTime) }}</span>
          </div>
          <h4>{{ k.title }}</h4>
          <p>{{ summary(k.content, 70) }}</p>
          <small class="kstat">浏览 {{ k.viewCount ?? 0 }}</small>
        </article>
      </div>
      <el-empty v-else description="暂无专家知识" />
    </el-card>

    <!-- ===========================================================
         区块 5 · 农业知识展示（按分类 + 热度组合）
         数据源：GET /api/home/knowledge?limit=20
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card">
      <template #header>
        <div class="card-title">
          <strong>农业知识</strong>
          <div class="card-tools">
            <el-radio-group v-model="agriCategory" size="small">
              <el-radio-button v-for="c in agriCategories" :key="c" :value="c">{{ c }}</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <el-skeleton v-if="loading.agriKnowledge" animated :rows="3" />
      <div v-else-if="previewAgri.length" class="knowledge-grid">
        <article v-for="k in previewAgri" :key="k.knowledgeId" class="knowledge-card-item" @click="openKnowledge(k)">
          <div class="cover" :class="coverClass(k.category)">
            <span>{{ k.category }}</span>
            <strong>{{ (k.title || '农业知识').slice(0, 2) }}</strong>
          </div>
          <div class="kc-body">
            <h4>{{ k.title }}</h4>
            <p>{{ summary(k.content, 80) }}</p>
            <div class="kc-foot">
              <small>{{ formatDateTime(k.createTime) }}</small>
              <small>热度 {{ k.viewCount ?? 0 }}</small>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无农业知识" />

    </el-card>

    <!-- ===========================================================
         区块 6 · 助农电商（点击跳转商品列表页）
       =========================================================== -->
    <el-card shadow="never" class="content-card section-card trade-banner-card">
      <div class="trade-banner">
        <div class="tb-text">
          <span class="eyebrow">助农电商</span>
          <h3>把田间的好货，送到城市的餐桌</h3>
          <p>产地直发、按类目筛选，支持在线下单与议价。</p>
          <el-button type="primary" size="large" @click="goTrade">前往商品列表</el-button>
        </div>
        <div class="tb-photo" aria-hidden="true">
          <span class="ph ph-1" /><span class="ph ph-2" /><span class="ph ph-3" />
        </div>
      </div>
    </el-card>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { getExperts } from '@/api/expert'
import { formatDateTime, isReadable, summary } from '@/utils/text'

/* ====================================================================
 * 全局开关与状态
 * ====================================================================
 * 演示模式约定：见 .env.development 的 VITE_USE_DEMO。
 *   - true：真接口失败 / 返回空数组 时回退到本地演示数据并标记 demoMode；
 *   - false：失败时保留空态，避免掩盖接口故障（与 FinanceApply / Knowledge /
 *           MyApplies 三个参考页保持一致）。
 * 新闻区块特殊：后端没有 /home/news 接口，常驻 demo 模式。
 */
const useDemo = import.meta.env.VITE_USE_DEMO === 'true'
const router = useRouter()

// 六个区块独立的加载态与演示态
const loading = reactive({
  news: false,
  product: false,
  expert: false,
  expertKnowledge: false,
  agriKnowledge: false
})
const demoMode = reactive({
  news: false,
  product: false,
  expert: false,
  expertKnowledge: false,
  agriKnowledge: false
})

const anyDemo = computed(() => Object.values(demoMode).some(Boolean))

/* ====================================================================
 * 通用工具
 * ==================================================================== */
function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

function setDemo(key, value) {
  demoMode[key] = value
}

/** picsum 随机图：基于种子，同 id 稳定同图（避免轮播切换时换图） */
function randomPic(seed) {
  return `https://picsum.photos/seed/${encodeURIComponent(String(seed))}/800/400`
}

/** 利率格式化：0.045 → "4.50"，"4.5" → "4.50" */
function formatRate(value) {
  const num = Number(value || 0)
  const percent = num < 1 ? num * 100 : num
  return percent.toFixed(2)
}

/** 金额格式化：300000 → "30万"，10000 → "1万" */
function formatMoney(value) {
  const num = Number(value || 0)
  if (num >= 10000) {
    return `${(num / 10000).toFixed(num % 10000 === 0 ? 0 : 1)}万`
  }
  return `${num}`
}

/* ====================================================================
 * 区块 1 · 新闻轮播（接口待开发，常驻演示）
 * ====================================================================
 * 字段权重用于"按排序权重轮播"：权重大的优先展示。
 * 跳转详情：路由里暂无新闻详情，先统一跳 /expert/knowledge，等
 * 新闻详情接口与路由就绪后再切真路由。 */
const newsList = ref([])
const activeNewsIndex = ref(0)

const DEMO_NEWS = [
  {
    id: 'demo-news-1',
    weight: 99,
    title: '融销通一期上线试运行公告',
    summary: '平台进入模拟运行环境，农户可体验融资申请、专家咨询与农产品交易全流程。',
    image: randomPic('demo-news-1'),
    href: '/expert/knowledge'
  },
  {
    id: 'demo-news-2',
    weight: 88,
    title: '联合贷款撮合规则升级',
    summary: '联合融资人匹配新增"经营品类"与"所在地区"权重，匹配结果更精准。',
    image: randomPic('demo-news-2'),
    href: '/expert/knowledge'
  },
  {
    id: 'demo-news-3',
    weight: 76,
    title: '平台引入第三方农业保险服务',
    summary: '为融资申请提供风险保障，降低联合小组的连带违约风险。',
    image: randomPic('demo-news-3'),
    href: '/expert/knowledge'
  }
]

async function loadNews() {
  loading.news = true
  // 新闻接口后端尚未实现 → 直接演示数据 + 标记
  try {
    // 预留：未来对接 GET /api/home/news
    // const data = await request.get('/home/news')
    // newsList.value = normalizeList(data).filter(...)
    // if (!newsList.value.length && useDemo) { ... }
    throw new Error('news 接口待开发')
  } catch {
    newsList.value = [...DEMO_NEWS].sort((a, b) => b.weight - a.weight)
    setDemo('news', true)
  } finally {
    loading.news = false
  }
}

function openNews(item) {
  if (!item) return
  if (item.href) router.push(item.href)
}

/* ====================================================================
 * 区块 2 · 金融产品
 * ==================================================================== */
const productList = ref([])

const DEMO_PRODUCTS = [
  { loanProductId: 1, productName: '惠农联保贷',     annualRate: 0.0435, maxAmount: 300000, termMonths: 36, description: '农户实名认证通过，3 户以上组成联合贷款小组', status: 1 },
  { loanProductId: 2, productName: '助农经营贷',     annualRate: 0.0490, maxAmount: 500000, termMonths: 60, description: '有稳定农产品销售流水，经营主体审核通过',     status: 1 },
  { loanProductId: 3, productName: '乡村振兴小额贷', annualRate: 0.0385, maxAmount: 100000, termMonths: 24, description: '农户实名认证通过，无不良记录',             status: 1 }
]

async function loadProducts() {
  loading.product = true
  try {
    const data = await request.get('/finance/loan-products')
    productList.value = normalizeList(data).map((item) => ({
      id:    item.loanProductId ?? item.productId ?? item.id,
      name:  item.productName   ?? item.name       ?? '',
      rate:  formatRate(item.annualRate ?? item.rate),
      amountRange: `最高 ${formatMoney(item.maxAmount)} 元`,
      termRange:   `${item.termMonths ?? 0} 个月`,
      condition:   item.description ?? item.condition ?? '详见产品说明',
      status:      item.status
    }))
    if (!productList.value.length && useDemo) {
      productList.value = mapDemoProducts(DEMO_PRODUCTS)
      setDemo('product', true)
    }
  } catch (err) {
    console.error('金融产品加载失败：', err)
    productList.value = []
    if (useDemo) {
      ElMessage.warning('金融产品加载失败，已显示演示数据')
      productList.value = mapDemoProducts(DEMO_PRODUCTS)
      setDemo('product', true)
    }
  } finally {
    loading.product = false
  }
}

function mapDemoProducts(list) {
  return list.map((item) => ({
    id:    item.loanProductId,
    name:  item.productName,
    rate:  formatRate(item.annualRate),
    amountRange: `最高 ${formatMoney(item.maxAmount)} 元`,
    termRange:   `${item.termMonths} 个月`,
    condition:   item.description,
    status:      item.status
  }))
}

/* ====================================================================
 * 区块 3 · 专家一览
 * ====================================================================
 * 数据源：GET /api/expert/experts（ExpertVO），姓名/头像由服务端一次装配好。
 * 首页只预览前 expertPageSize 条，客户端不再逐行补全用户信息。 */
const expertList = ref([])                 // 已含 realName/avatarUrl
const expertDomain = ref('')
const expertPageSize = 3

// 领域下拉的选项：用「只增不减的并集」而不是从当前列表推导。
// 一旦按某领域筛过，返回的列表里就只剩那一个领域，若从列表推导，
// 下拉会塌成单选项、用户切不回别的领域（只能先清空）。
const expertDomains = ref([])

// 首页预览只展示前 3 条；点击卡片或"查看更多"跳转 /expert/qa
const previewExperts = computed(() => expertList.value.slice(0, expertPageSize))

// 头像加载失败标记（按 userId 记），保证只在该 userId 的图加载失败时显示首字
const uaBroken = reactive({})
function uaStyle(size) {
  const n = Number(size)
  return {
    width: `${n}px`,
    height: `${n}px`,
    fontSize: `${Math.max(11, Math.round(n * 0.42))}px`
  }
}

const DEMO_EXPERTS_RAW = [
  { expertId: 1, userId: 6,  expertDomain: '种植技术', introduction: '擅长水稻、柑橘等作物的高效栽培与病虫害识别。', workloadStat: '已回答 38 问 / 接待 12 预约' },
  { expertId: 2, userId: 7,  expertDomain: '植物保护', introduction: '主要从事植物病虫害综合防治与绿色防控推广。',     workloadStat: '已回答 27 问 / 接待 9 预约' },
  { expertId: 3, userId: 8,  expertDomain: '土壤肥料', introduction: '专注土壤改良与测土配方施肥，提供田间指导。',     workloadStat: '已回答 22 问 / 接待 6 预约' },
  { expertId: 4, userId: 9,  expertDomain: '养殖技术', introduction: '畜禽健康养殖与防疫程序设计方向。',               workloadStat: '已回答 31 问 / 接待 10 预约' },
  { expertId: 5, userId: 10, expertDomain: '农业金融', introduction: '熟悉农业贷款贴息政策与联合贷款机制。',           workloadStat: '已回答 19 问 / 接待 4 预约' }
]

/** 演示数据没有对应的 t_user 行，姓名统一用与真实链路一致的占位。 */
function demoExperts() {
  return DEMO_EXPERTS_RAW.map((e) => ({ ...e, realName: '农技专家', avatarUrl: '' }))
}

async function loadExperts() {
  loading.expert = true
  try {
    const data = await getExperts(expertDomain.value)
    expertList.value = normalizeList(data).map((e) => ({
      expertId: e.expertId,
      userId: e.userId,
      expertDomain: e.expertDomain,
      introduction: e.introduction,
      workloadStat: e.workloadStat,
      // 后端已保证 realName 非 null（可能为空串），这里再兜一层默认值
      realName: e.realName || '农技专家',
      avatarUrl: e.avatarUrl || ''
    }))
    if (!expertList.value.length && useDemo) {
      expertList.value = demoExperts()
      setDemo('expert', true)
    }
  } catch (err) {
    // request.js 已统一弹过错误提示，这里只负责兜底，不重复打扰用户
    console.error('专家列表加载失败：', err)
    expertList.value = useDemo ? demoExperts() : []
    if (useDemo) setDemo('expert', true)
  } finally {
    loading.expert = false
  }

  const seen = new Set(expertDomains.value)
  expertList.value.forEach((e) => {
    if (isReadable(e.expertDomain)) seen.add(e.expertDomain)
  })
  expertDomains.value = [...seen]
}

function goExpertDetail(expert) {
  // 跳转到专家问答页，并把 expertId 通过 query 传入
  router.push({ path: '/expert/qa', query: { expertId: expert.expertId } })
}

/* ====================================================================
 * 区块 4 · 专家知识（authorId 是专家主键，按 createTime 倒序）
 * ====================================================================
 * 后端只有 /home/knowledge 一条公共知识接口；"专家知识"语义上指由专家
 * (t_expert 内 userId) 发布的内容，前端按 authorId 过滤。 */
const expertKnowledgeList = ref([])

const DEMO_EXPERT_KNOWLEDGE = [
  { knowledgeId: 9001, title: '水稻分蘖期水分与氮肥管理',  category: '种植技术', content: '分蘖期是水稻产量的关键阶段，建议保持浅水层并适当控氮，避免无效分蘖。', viewCount: 412, authorId: 6,  auditStatus: '已通过', createTime: '2026-08-30 10:15:00' },
  { knowledgeId: 9002, title: '生猪非洲猪瘟常态化防控要点', category: '养殖技术', content: '防控以生物安全为核心，落实"四不漏、三消毒、两记录"日常管理。',         viewCount: 388, authorId: 9,  auditStatus: '已通过', createTime: '2026-08-28 10:10:00' },
  { knowledgeId: 9003, title: '冬小麦春季返青追肥技术',    category: '土壤肥料', content: '返青期以氮肥为主，配合适量钾肥；墒情不足时先浇水后追肥。',              viewCount: 256, authorId: 8,  auditStatus: '已通过', createTime: '2026-08-21 10:21:00' },
  { knowledgeId: 9004, title: '苹果园秋季基肥施用方法',    category: '种植技术', content: '秋季基肥以有机肥为主，配合复合肥；放射状沟施有利于根系扩展。',           viewCount: 198, authorId: 6,  auditStatus: '已通过', createTime: '2026-07-31 10:03:00' },
  { knowledgeId: 9005, title: '设施蔬菜连作障碍治理',       category: '土壤肥料', content: '采用石灰氮高温闷棚、秸秆还田与生物菌肥协同，逐年缓解连作障碍。',          viewCount: 175, authorId: 8,  auditStatus: '已通过', createTime: '2026-07-27 10:48:00' },
  { knowledgeId: 9006, title: '大棚番茄灰霉病综合防治',    category: '植物保护', content: '以农业防治为基础，配合熏蒸与生物农药，避开花期用药。',                   viewCount: 312, authorId: 7,  auditStatus: '已通过', createTime: '2026-07-06 10:32:00' }
]

async function loadExpertKnowledge() {
  loading.expertKnowledge = true
  let raw = []
  try {
    const data = await request.get('/home/knowledge', { params: { limit: 20 } })
    raw = normalizeList(data)
    if (!raw.length && useDemo) {
      raw = DEMO_EXPERT_KNOWLEDGE
      setDemo('expertKnowledge', true)
    }
  } catch (err) {
    console.error('专家知识加载失败：', err)
    raw = []
    if (useDemo) {
      ElMessage.warning('专家知识加载失败，已显示演示数据')
      raw = DEMO_EXPERT_KNOWLEDGE
      setDemo('expertKnowledge', true)
    }
  }

  // 过滤：authorId 落在专家列表里；isReadable 避开 17/18 那种乱码记录。
  //
  // ⚠️ 这里比对的是 **expertId（专家主键）**，不是 userId。
  // `t_knowledge.author_id` 的外键是 `fk_knowledge_author_id → t_expert(expert_id)`
  // （schema.sql），后端写入时也是 `setAuthorId(expert.getExpertId())`
  // （KnowledgeServiceImpl），审核模块再用 toUserIdByExpertId(authorId) 换算成用户 ——
  // 即 authorId 全链路都是专家主键。此前这里拿它去比 expertList 的 userId，
  // 只因专家列表恒为空（接口 404）而被 `size === 0` 短路掩盖，从没真正生效过。
  const expertIds = new Set(
    expertList.value.map((e) => Number(e.expertId)).filter(Boolean)
  )
  const filtered = raw
    .filter((k) => k.auditStatus === '已通过' && isReadable(k.title))
    .filter((k) => (useDemo && demoMode.expertKnowledge) || expertIds.size === 0
      ? true
      : expertIds.has(Number(k.authorId)))
    .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))

  expertKnowledgeList.value = filtered.slice(0, 3)
  loading.expertKnowledge = false
}

/* ====================================================================
 * 区块 5 · 农业知识（按分类 tabs + viewCount 热度组合排序）
 * ==================================================================== */
const allAgriKnowledge = ref([])
const agriCategory = ref('全部')
const agriCategories = ['全部', '种植技术', '植物保护', '土壤肥料', '养殖技术', '农业金融']
const agriPageSize = 3

const filteredAgri = computed(() => {
  const list = allAgriKnowledge.value
    .filter((k) => k.auditStatus === '已通过' && isReadable(k.title))
    .filter((k) => agriCategory.value === '全部' || k.category === agriCategory.value)
    // 热度组合：viewCount 倒序，相同时按 createTime 倒序
    .sort((a, b) => {
      const va = Number(a.viewCount || 0)
      const vb = Number(b.viewCount || 0)
      if (vb !== va) return vb - va
      return String(b.createTime).localeCompare(String(a.createTime))
    })
  return list
})

// 首页预览只展示前 3 条；点击卡片或"查看更多"跳转 /expert/knowledge
const previewAgri = computed(() => filteredAgri.value.slice(0, agriPageSize))

function coverClass(categoryName) {
  const idx = agriCategories.indexOf(categoryName)
  return idx > 0 ? `cover-${(idx - 1) % 5}` : 'cover-0'
}

const DEMO_AGRI_KNOWLEDGE = [
  { knowledgeId: 8001, title: '家庭农场财务核算基础', category: '农业金融', content: '围绕成本归集、收入确认与利润分配，建立简易台账即可满足小农场合规需要。', viewCount: 3184, authorId: 2, auditStatus: '已通过', createTime: '2026-08-12 10:21:00' },
  { knowledgeId: 8002, title: '惠农贷款政策解读与申请指引', category: '农业金融', content: '贴息额度、申报材料、审批流程与常见被驳回原因解析。', viewCount: 2746, authorId: 2, auditStatus: '已通过', createTime: '2026-08-24 10:09:00' },
  { knowledgeId: 8003, title: '大棚番茄灰霉病综合防治', category: '植物保护', content: '以农业防治为基础，配合熏蒸与生物农药，避开花期用药。', viewCount: 2707, authorId: 3, auditStatus: '已通过', createTime: '2026-07-06 10:32:00' },
  { knowledgeId: 8004, title: '生猪非洲猪瘟常态化防控要点', category: '养殖技术', content: '防控以生物安全为核心，落实日常管理与消毒记录。', viewCount: 2732, authorId: 1, auditStatus: '已通过', createTime: '2026-08-28 10:10:00' },
  { knowledgeId: 8005, title: '苹果园秋季基肥施用方法', category: '种植技术', content: '秋季基肥以有机肥为主，配合复合肥；放射状沟施有利于根系扩展。', viewCount: 2127, authorId: 5, auditStatus: '已通过', createTime: '2026-07-31 10:03:00' },
  { knowledgeId: 8006, title: '农业保险投保与理赔实务', category: '农业金融', content: '常见险种、保单要素与出险后取证理赔的关键步骤。', viewCount: 1962, authorId: 3, auditStatus: '已通过', createTime: '2026-07-04 10:10:00' },
  { knowledgeId: 8007, title: '中药材 GAP 种植规范要点', category: '种植技术', content: '产地环境、农事操作、采收与初加工的全过程合规要点。', viewCount: 444, authorId: 5, auditStatus: '已通过', createTime: '2026-08-30 10:15:00' },
  { knowledgeId: 8008, title: '冬小麦春季返青追肥技术', category: '土壤肥料', content: '返青期以氮肥为主，配合适量钾肥；墒情不足时先浇水后追肥。', viewCount: 359, authorId: 4, auditStatus: '已通过', createTime: '2026-08-21 10:21:00' },
  { knowledgeId: 8009, title: '设施蔬菜连作障碍治理', category: '土壤肥料', content: '采用石灰氮高温闷棚、秸秆还田与生物菌肥协同，逐年缓解连作障碍。', viewCount: 579, authorId: 4, auditStatus: '已通过', createTime: '2026-07-27 10:48:00' },
  { knowledgeId: 8010, title: '农产品采收后的预冷与保鲜', category: '种植技术', content: '叶菜类采收后尽快预冷，不同品类不可混装运输。', viewCount: 223, authorId: 1, auditStatus: '已通过', createTime: '2026-08-31 09:00:00' }
]

async function loadAgriKnowledge() {
  loading.agriKnowledge = true
  try {
    const data = await request.get('/home/knowledge', { params: { limit: 30 } })
    allAgriKnowledge.value = normalizeList(data)
    if (!allAgriKnowledge.value.length && useDemo) {
      allAgriKnowledge.value = DEMO_AGRI_KNOWLEDGE
      setDemo('agriKnowledge', true)
    }
  } catch (err) {
    console.error('农业知识加载失败：', err)
    allAgriKnowledge.value = []
    if (useDemo) {
      ElMessage.warning('农业知识加载失败，已显示演示数据')
      allAgriKnowledge.value = DEMO_AGRI_KNOWLEDGE
      setDemo('agriKnowledge', true)
    }
  } finally {
    loading.agriKnowledge = false
  }
}

/* ====================================================================
 * 跳转
 * ==================================================================== */
function goFinanceList() { router.push('/finance/apply') }
function goKnowledge()    { router.push('/expert/knowledge') }
function goTrade()       { router.push('/trade/products') }
function openKnowledge(item) {
  // 首页未提供"知识详情"路由；跳到列表页并由用户按需搜索标题
  router.push({ path: '/expert/knowledge', query: { knowledgeId: item.knowledgeId } })
}

onMounted(async () => {
  // 区块 1：新闻（无接口，常驻 demo）
  loadNews()
  // 区块 3 专家列表是区块 4 的前置，按顺序加载
  await loadExperts()
  await loadExpertKnowledge()
  loadProducts()
  loadAgriKnowledge()
})
</script>

<style scoped>
/* 样式严格限制在 scoped 内，不污染其他页面 */
.home-page { color: var(--el-text-color-primary); }

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 18px;
}
.page-head h2 { margin: 0; font-size: 24px; }
.page-head p  { margin: 8px 0 0; color: var(--el-text-color-secondary); line-height: 1.7; }
.head-tags    { display: flex; align-items: center; gap: 8px; }

.content-card  { border-radius: 14px; }
.section-card  { margin-top: 18px; }

.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.card-tools { display: flex; align-items: center; gap: 12px; }

.pagination {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}

/* ===== 区块 1 · 新闻轮播 ===== */
.banner {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: flex-end;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
}
.banner-mask {
  width: 100%;
  padding: 18px 22px;
  box-sizing: border-box;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.72));
  color: #fff;
}
.banner-meta {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: #f6d97b;
}
.banner-title { margin: 0 0 8px; font-size: 20px; }
.banner-summary { margin: 0; font-size: 14px; opacity: 0.9; line-height: 1.7; }

/* ===== 区块 2 · 金融产品 ===== */
.product-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: 0.2s;
}
.product-card:hover { border-color: var(--el-color-primary); }
.product-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.product-name { font-size: 15px; font-weight: 600; color: #303133; }
.product-rate { color: #f56c6c; margin-bottom: 12px; line-height: 1; }
.product-rate .num   { font-size: 26px; font-weight: 700; }
.product-rate .unit  { font-size: 14px; margin-left: 2px; }
.product-rate .label { margin-left: 8px; font-size: 12px; color: #909399; }
.product-meta {
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: #606266;
}
.product-meta li {
  display: flex;
  justify-content: flex-start;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}
.product-meta li span { color: #909399; }
.product-condition {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.condition-label { color: #606266; }

/* ===== 区块 3 · 专家一览 ===== */
.expert-card { margin-bottom: 16px; transition: 0.2s; cursor: pointer; }
.expert-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,.06); }
.expert-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.expert-id { display: flex; flex-direction: column; gap: 4px; }
.expert-id strong { font-size: 15px; color: #303133; }
.expert-intro {
  min-height: 42px;
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.expert-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.expert-footer small { color: var(--el-text-color-secondary); font-size: 12px; }

/* ===== 区块 4 · 专家知识（横排小卡） ===== */
.knowledge-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.knowledge-mini {
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: 0.2s;
}
.knowledge-mini:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.knowledge-mini .kmeta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.knowledge-mini h4 { margin: 0 0 6px; font-size: 15px; line-height: 1.5; }
.knowledge-mini p  {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  min-height: 42px;
}
.kstat { color: #9a6b24; font-size: 12px; }

/* ===== 区块 5 · 农业知识（卡片网格） ===== */
.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.knowledge-card-item {
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition: 0.2s;
}
.knowledge-card-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 26px rgba(29, 70, 46, .1);
}
.cover {
  height: 100px;
  padding: 14px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  color: #fff;
  background: linear-gradient(135deg, #2d7a4c, #73b586);
}
.cover-1 { background: linear-gradient(135deg, #91602a, #d4a164); }
.cover-2 { background: linear-gradient(135deg, #2c6679, #78adba); }
.cover-3 { background: linear-gradient(135deg, #674b7d, #a991ba); }
.cover-4 { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.cover span  { padding: 4px 8px; border-radius: 999px; background: rgba(255,255,255,.18); font-size: 12px; }
.cover strong { align-self: flex-end; font-size: 28px; opacity: 0.25; }
.kc-body { padding: 14px; }
.kc-body h4 {
  min-height: 44px;
  margin: 0 0 6px;
  font-size: 15px;
  line-height: 1.5;
}
.kc-body p {
  min-height: 56px;
  margin: 0 0 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.kc-foot {
  display: flex;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ===== 区块 6 · 助农电商横幅 ===== */
.trade-banner-card { padding: 0; }
.trade-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 24px;
  color: #fff;
  background: linear-gradient(125deg, #15452b, #2f8a59);
  border-radius: 14px;
}
.tb-text { max-width: 540px; }
.tb-text .eyebrow { font-size: 13px; color: #cdebd6; }
.tb-text h3 { margin: 8px 0; font-size: 24px; }
.tb-text p  { margin: 0 0 16px; color: rgba(255,255,255,.78); line-height: 1.7; }
.tb-photo {
  position: relative;
  width: 220px;
  height: 130px;
}
.tb-photo .ph {
  position: absolute;
  border-radius: 12px;
  box-shadow: 0 12px 24px rgba(0,0,0,.18);
}
.tb-photo .ph-1 {
  top: 0; left: 0;
  width: 120px; height: 90px;
  background: linear-gradient(135deg, #f0c674, #b8853b);
  transform: rotate(-6deg);
}
.tb-photo .ph-2 {
  top: 20px; right: 0;
  width: 110px; height: 80px;
  background: linear-gradient(135deg, #82c99e, #356b4a);
  transform: rotate(5deg);
}
.tb-photo .ph-3 {
  bottom: 0; left: 60px;
  width: 90px; height: 60px;
  background: linear-gradient(135deg, #c1d9aa, #6f9863);
  transform: rotate(-2deg);
}

@media (max-width: 980px) {
  .knowledge-row,
  .knowledge-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 680px) {
  .knowledge-row,
  .knowledge-grid { grid-template-columns: 1fr; }
  .trade-banner   { flex-direction: column; align-items: stretch; }
  .tb-photo       { width: 100%; height: 100px; }
}
</style>
