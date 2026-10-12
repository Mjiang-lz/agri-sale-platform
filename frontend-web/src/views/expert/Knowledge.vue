<template>
  <div class="knowledge-page">
    <div class="hero">
      <div>
        <span class="eyebrow">农业知识库</span>
        <h2>把实用的农业经验送到每一块田里</h2>
        <p>按分类和关键词检索平台已审核通过的种植、养殖、病虫害防治、市场行情与政策知识</p>
      </div>
      <div class="hero-stat">
        <strong>{{ filteredList.length }}</strong>
        <span>{{ filteredList.length === totalApproved ? '条已审核知识' : `条结果 / 全部 ${totalApproved} 条` }}</span>
      </div>
    </div>

    <ExpertKnowledgeEditor v-if="isExpert" :submissions="visibleSubmissions" @created="handleKnowledgeCreated" @dismiss="handleKnowledgeDismiss" />

    <el-alert
      v-if="demoMode"
      class="demo-alert"
      title="当前页面显示的是演示数据"
      description="真实接口失败或返回空列表，且 VITE_USE_DEMO=true。请先检查后端和 rxt.t_knowledge 数据。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-card shadow="never" class="knowledge-card">
      <template #header>
        <div class="toolbar">
          <el-radio-group v-model="category" @change="handleCategoryChange">
            <el-radio-button v-for="item in categories" :key="item" :value="item">{{ item }}</el-radio-button>
          </el-radio-group>
          <el-input v-model="keyword" clearable placeholder="搜索标题或正文" style="width: 280px" />
        </div>
      </template>

      <div v-loading="loading" class="knowledge-grid">
        <!--
          ★ 2026-10-12：卡片点击从「打开弹窗」改为「跳独立页面 /knowledge/:id」。
          用户要求「每个帖子知识新起一个页面」—— 弹窗没有自己的地址，
          读完一篇没法把链接发给别人，浏览器前进后退也管不了它。
        -->
        <article v-for="item in pagedList" :key="item.knowledgeId" class="knowledge-item" @click="goDetail(item)">
          <!--
            ★ 2026-10-12：封面从「只有分类渐变块」改为「有真配图就显示拍图」。
            起因是当天的另一个修复 —— 1~18 号知识的 image_url 已回填为真实 OBS 地址，
            首页与详情页都会显示它；这一页若还只画渐变块，同一份数据就有了两副面孔。
            没有真图（含加载失败、以及演示数据）时，仍然退回原来的渐变块 + 分类水印，
            所以格子不会开天窗。
          -->
          <div class="cover" :class="coverClass(item.category)">
            <img
              v-if="coverOf(item)"
              class="cover-img"
              :src="coverOf(item)"
              alt=""
              loading="lazy"
              @error="onCoverError(item)"
            />
            <div v-if="coverOf(item)" class="cover-scrim"></div>
            <span>{{ item.category }}</span>
            <strong v-if="!coverOf(item)">{{ (item.title || '农业知识').slice(0, 2) }}</strong>
          </div>
          <div class="item-body">
            <div class="meta">
              <span>发布时间：{{ formatDateTime(item.createTime) }}</span>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ summary(item.content) }}</p>
            <div class="item-footer">
              <span class="verified">平台审核通过</span>
              <el-button link type="primary">阅读全文</el-button>
            </div>
          </div>
        </article>
        <el-empty v-if="!loading && !pagedList.length" description="没有找到符合条件的农业知识" />
      </div>

      <div class="pagination">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredList.length" background layout="prev, pager, next" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ExpertKnowledgeEditor from '@/components/expert/ExpertKnowledgeEditor.vue'
import { getKnowledgeList, getMyKnowledge } from '@/api/knowledge'
import { knowledgeImage, knowledgeLocalImage } from '@/utils/imagery'
import { useUserStore } from '@/store/user'

const useDemo = import.meta.env.VITE_USE_DEMO === 'true'
const router = useRouter()
const userStore = useUserStore()
const isExpert = computed(() => userStore.profile?.roleName === '专家')
const mySubmissions = ref([])
const hiddenSubmissionIds = ref([])
const hiddenSubmissionKey = 'rxt_hidden_knowledge_submission_ids'
let refreshTimer = null
const demoMode = ref(false)
const loading = ref(false)
const knowledgeList = ref([])
const category = ref('全部')
const keyword = ref('')
const totalApproved = ref(0)
const page = ref(1)
const pageSize = ref(6)
const categories = ['全部', '种植技术', '养殖技术', '病虫害防治', '市场行情', '政策解读']

function demoKnowledge() {
  return [
    { knowledgeId: 1, title: '柑橘裂果的成因与预防要点', category: '种植技术', authorId: 1, auditStatus: '已通过', viewCount: 386, createTime: '2026-08-28 09:00:00', content: '裂果多发生在果实膨大期至转色期，主要诱因是土壤水分剧烈波动。建议保持土壤湿度稳定，采用滴灌少量多次；均衡补钙；雨后及时排水；合理疏果。' },
    { knowledgeId: 2, title: '水稻稻瘟病的识别与绿色防控', category: '病虫害防治', authorId: 1, auditStatus: '已通过', viewCount: 412, createTime: '2026-08-28 09:30:00', content: '稻瘟病分为苗瘟、叶瘟、节瘟与穗颈瘟。叶瘟典型症状为梭形病斑、中央灰白边缘褐色。防控以抗病品种为基础，配合合理密植、控氮增磷钾。' },
    { knowledgeId: 10, title: '散养土鸡的防疫程序安排', category: '养殖技术', authorId: 2, auditStatus: '已通过', viewCount: 447, createTime: '2026-09-01 10:00:00', content: '建议按日龄做好马立克、新城疫、传支和禽流感等免疫，同时做好鸡舍通风与垫料管理，控制饲养密度。' },
    { knowledgeId: 8, title: '绿色食品认证的申报条件', category: '政策解读', authorId: 1, auditStatus: '已通过', viewCount: 465, createTime: '2026-08-31 10:00:00', content: '申报绿色食品需满足产地环境、生产过程、产品检测和生产档案等条件，认证有效期三年。' },
    { knowledgeId: 7, title: '农产品采收后的预冷与保鲜', category: '市场行情', authorId: 1, auditStatus: '已通过', viewCount: 223, createTime: '2026-08-31 09:00:00', content: '叶菜类采收后应尽快预冷，运输和贮藏时注意不同品类不可混装。' }
  ]
}

const filteredList = computed(() => {
  const key = keyword.value.trim()
  return knowledgeList.value.filter((item) => {
    const categoryMatch = category.value === '全部' || item.category === category.value
    const keywordMatch = !key || `${item.title || ''}${item.content || ''}`.includes(key)
    return categoryMatch && keywordMatch
  })
})

const total = computed(() => filteredList.value.length)
const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})
const visibleSubmissions = computed(() => mySubmissions.value.filter((item) => !hiddenSubmissionIds.value.includes(Number(item.knowledgeId))))

/**
 * 从本地后端读取 rxt.t_knowledge 数据。
 * 首次加载读取全部已通过知识，分类和关键词在内存中筛选，避免分类切换重复请求。
 * 演示数据只有 VITE_USE_DEMO=true 时才会启用，并显示顶部提示。
 */
async function loadList() {
  loading.value = true
  demoMode.value = false
  // 重新拉列表时清掉失败记录：上一轮可能是网络抖动，给 OBS 真图一次重试的机会
  coverFailedIds.value = []
  try {
    const data = await getKnowledgeList()
    const list = Array.isArray(data) ? data : data?.records || []
    knowledgeList.value = list.filter((item) => item.auditStatus === '已通过')
    if (!knowledgeList.value.length && useDemo) {
      knowledgeList.value = demoKnowledge()
      demoMode.value = true
    }
    totalApproved.value = knowledgeList.value.length
  } catch {
    knowledgeList.value = useDemo ? demoKnowledge() : []
    totalApproved.value = knowledgeList.value.length
    if (useDemo) demoMode.value = true
  } finally {
    loading.value = false
  }
}

function handleCategoryChange() {
  page.value = 1
}

/**
 * 打开单篇知识：跳到独立页面 /knowledge/:id。
 *
 * 这里原来是「开弹窗 + 调详情接口」，改成整页跳转后两件事都不用做了 ——
 * 详情页自己会拉数据、自己管加载与错误态。列表页从此只负责列表。
 */
function goDetail(item) {
  if (item?.knowledgeId) router.push(`/knowledge/${item.knowledgeId}`)
}

/**
 * 封面加载失败的 knowledgeId 集合。
 *
 * 2026-10-12：1~18 号知识的 image_url 回填成了 OBS 绝对地址，但 OBS 对象可能
 * 断网取不到、也可能被后来清理掉 —— 那时 `<img>` 会裂图（比没图更难看）。
 * 所以失败一次就记下来，改用本地实拍图；再失败也不重试，避免无限循环。
 */
const coverFailedIds = ref([])

/**
 * 当前应该给这张卡渲染哪个封面。
 *
 * - 正常：knowledgeImage(item) —— ① 接口 imageUrl（真 OBS 图）→ ② 标题关键词本地图 → ③ 分类渐变封面，恒非空
 * - 加载失败过：knowledgeLocalImage(item) —— **跳过第 ① 级**，只走 ② → ③
 *
 * ⚠️ 失败后必须跳过 ①：knowledgeImage() 第一步就是那个坏掉的 OBS 地址，
 * 再走一遍只会「失败 → 换成同一个 URL → 再失败」。这与 BizHome 里用
 * dataset.localFallback 打标记是同一个用意。
 */
function coverOf(item) {
  if (coverFailedIds.value.includes(Number(item?.knowledgeId))) return knowledgeLocalImage(item)
  return knowledgeImage(item)
}

function onCoverError(item) {
  const id = Number(item?.knowledgeId)
  // 只记一次：本地兜底图若也失败，不再重复 push 同一个 id（否则每次 error 都触发重渲染）
  if (!Number.isFinite(id) || coverFailedIds.value.includes(id)) return
  coverFailedIds.value = [...coverFailedIds.value, id]
}

function coverClass(categoryName) {
  const coverIndex = {
    '种植技术': 0,
    '养殖技术': 1,
    '病虫害防治': 2,
    '市场行情': 3,
    '政策解读': 4
  }[categoryName] ?? 0
  return `cover-${coverIndex % 5}`
}

function summary(value) {
  const content = String(value || '').replace(/\s+/g, ' ')
  return content.length > 100 ? `${content.slice(0, 100)}...` : content
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadMyKnowledge() {
  if (!isExpert.value) return
  try {
    const data = await getMyKnowledge()
    mySubmissions.value = Array.isArray(data) ? data : data?.records || []
  } catch {
    mySubmissions.value = []
  }
}

async function handleKnowledgeCreated() {
  await loadMyKnowledge()
  await loadList()
}

function handleKnowledgeDismiss(item) {
  const id = Number(item.knowledgeId)
  if (!hiddenSubmissionIds.value.includes(id)) {
    hiddenSubmissionIds.value.push(id)
    localStorage.setItem(hiddenSubmissionKey, JSON.stringify(hiddenSubmissionIds.value))
  }
}

onMounted(() => {
  try {
    hiddenSubmissionIds.value = JSON.parse(localStorage.getItem(hiddenSubmissionKey) || '[]').map(Number)
  } catch {
    hiddenSubmissionIds.value = []
  }
  loadList()
  loadMyKnowledge()
  refreshTimer = window.setInterval(loadMyKnowledge, 30000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<style scoped>
.hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-bottom: 18px; padding: 28px; color: #fff; border-radius: 18px; background: linear-gradient(125deg, #15452b, #2f8a59); }
.eyebrow { font-size: 13px; color: #cdebd6; }
.hero h2 { margin: 10px 0 8px; font-size: 28px; }
.hero p { margin: 0; max-width: 720px; color: rgba(255,255,255,.74); line-height: 1.7; }
.hero-stat { min-width: 130px; padding: 15px; text-align: center; border: 1px solid rgba(255,255,255,.22); border-radius: 13px; background: rgba(255,255,255,.1); }
.hero-stat strong, .hero-stat span { display: block; }
.hero-stat strong { font-size: 28px; }
.hero-stat span { margin-top: 4px; color: rgba(255,255,255,.65); font-size: 12px; }
.demo-alert { margin-bottom: 16px; }
.knowledge-card { border-radius: 15px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.knowledge-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; min-height: 280px; }
.knowledge-item { overflow: hidden; border: 1px solid var(--el-border-color-light); border-radius: 14px; background: #fff; cursor: pointer; transition: .2s; }
.knowledge-item:hover { transform: translateY(-4px); box-shadow: 0 14px 30px rgba(29, 70, 46, .1); }
.cover { position: relative; overflow: hidden; height: 112px; padding: 16px; display: flex; justify-content: space-between; align-items: flex-start; color: #fff; background: linear-gradient(135deg, #2d7a4c, #73b586); }
/* 真配图铺满封面；分类角标与水印靠 z-index 浮在图上 */
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
/* 顶部/底部压一层很薄的暗角：分类角标是「半透明白底 + 白字」，
   直接压在浅色照片（如沙地、水面）上会看不清 —— 与轮播指示点踩过的是同一个坑 */
.cover-scrim { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0, 0, 0, .36) 0%, rgba(0, 0, 0, 0) 48%, rgba(0, 0, 0, .24) 100%); }
.cover > span, .cover > strong { position: relative; z-index: 1; }
.cover-1 { background: linear-gradient(135deg, #91602a, #d4a164); }
.cover-2 { background: linear-gradient(135deg, #2c6679, #78adba); }
.cover-3 { background: linear-gradient(135deg, #674b7d, #a991ba); }
.cover-4 { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.cover span { padding: 4px 8px; border-radius: 999px; background: rgba(255,255,255,.18); font-size: 12px; }
.cover strong { align-self: flex-end; font-size: 32px; opacity: .25; }
.item-body { padding: 15px; }
.meta, .item-footer { display: flex; justify-content: space-between; gap: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
.item-body h3 { min-height: 46px; margin: 12px 0 8px; font-size: 16px; line-height: 1.45; }
.item-body p { min-height: 64px; margin: 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.65; }
.item-footer { align-items: center; margin-top: 13px; padding-top: 11px; border-top: 1px solid var(--el-border-color-lighter); }
.verified { color: var(--el-color-success); }
@media (max-width: 980px) { .knowledge-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .hero, .toolbar { flex-direction: column; align-items: stretch; } .toolbar .el-input { width: 100% !important; } .knowledge-grid { grid-template-columns: 1fr; } }
</style>
