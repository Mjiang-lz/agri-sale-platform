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

    <ExpertKnowledgeEditor v-if="isExpert" :submissions="mySubmissions" @created="handleKnowledgeCreated" />

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
        <article v-for="item in pagedList" :key="item.knowledgeId" class="knowledge-item" @click="openDetail(item)">
          <div class="cover" :class="coverClass(item.category)">
            <span>{{ item.category }}</span>
            <strong>{{ (item.title || '农业知识').slice(0, 2) }}</strong>
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

    <!-- ★ 2026-10-10：由右侧抽屉改为居中弹窗（用户要求「不要采用抽屉式」）。
         抽屉的问题是形态本身：700px 宽的竖长条贴在屏幕右边，正文被压进窄栏，
         长文章要滚很久，读者视线也始终偏在屏幕一侧。
         弹窗居中后宽度更接近正文的舒适阅读宽度，配合 body 限高滚动，更像在读文章。
         注意 el-dialog 默认 teleport 到 body，`.el-dialog__body` 的限高规则
         只能写在文件末尾那个**非 scoped** 的 style 块里 —— scoped 选择器够不着组件内部结构。 -->
    <el-dialog
      v-model="detailVisible"
      title="农业知识详情"
      width="min(760px, 94%)"
      top="6vh"
      class="knowledge-detail-dialog"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-tag>{{ detail.category }}</el-tag>
          <h2 class="detail-title">{{ detail.title }}</h2>
          <div class="detail-meta">
            <span>发布时间：{{ formatDateTime(detail.createTime) }}</span>
          </div>
          <el-image v-if="detail.imageUrl" class="detail-image" :src="detail.imageUrl" fit="cover" />
          <el-divider />
          <div class="detail-content">{{ detail.content }}</div>
          <el-alert
            class="content-tip"
            type="info"
            :closable="false"
            show-icon
            title="内容说明"
            description="农业生产建议仅供参考，请结合当地气候、土壤和作物实际情况执行；涉及用药请遵守安全间隔期。"
          />
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import ExpertKnowledgeEditor from '@/components/expert/ExpertKnowledgeEditor.vue'
import { getKnowledgeDetail, getKnowledgeList } from '@/api/knowledge'
import { useUserStore } from '@/store/user'

const useDemo = import.meta.env.VITE_USE_DEMO === 'true'
const userStore = useUserStore()
const isExpert = computed(() => userStore.profile?.roleName === '专家')
const mySubmissions = ref([])
const mySubmissionKey = 'rxt_expert_knowledge_submissions'
const demoMode = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const knowledgeList = ref([])
const detail = ref(null)
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

/**
 * 从本地后端读取 rxt.t_knowledge 数据。
 * 首次加载读取全部已通过知识，分类和关键词在内存中筛选，避免分类切换重复请求。
 * 演示数据只有 VITE_USE_DEMO=true 时才会启用，并显示顶部提示。
 */
async function loadList() {
  loading.value = true
  demoMode.value = false
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

async function openDetail(item) {
  detail.value = item
  detailVisible.value = true
  detailLoading.value = true
  try {
    const data = await getKnowledgeDetail(item.knowledgeId)
    if (data) detail.value = data
  } catch {
    // 详情接口失败时保留列表行中的标题和摘要，不再切换到假数据。
  } finally {
    detailLoading.value = false
  }
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

function handleKnowledgeCreated(item) {
  mySubmissions.value.unshift(item)
  localStorage.setItem(mySubmissionKey, JSON.stringify(mySubmissions.value))
  loadList()
}

onMounted(() => {
  try {
    mySubmissions.value = JSON.parse(localStorage.getItem(mySubmissionKey) || '[]')
  } catch {
    mySubmissions.value = []
  }
  loadList()
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
.cover { height: 112px; padding: 16px; display: flex; justify-content: space-between; align-items: flex-start; color: #fff; background: linear-gradient(135deg, #2d7a4c, #73b586); }
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
.detail-title { margin: 14px 0 10px; line-height: 1.45; }
.detail-meta { display: flex; gap: 20px; color: var(--el-text-color-secondary); font-size: 13px; }
.detail-image { width: 100%; max-height: 300px; margin-top: 18px; border-radius: 12px; }
.detail-content { white-space: pre-wrap; line-height: 2; font-size: 16px; color: #35443b; }
.content-tip { margin-top: 28px; }
@media (max-width: 980px) { .knowledge-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .hero, .toolbar { flex-direction: column; align-items: stretch; } .toolbar .el-input { width: 100% !important; } .knowledge-grid { grid-template-columns: 1fr; } }
</style>

<!--
  这个块**故意不加 scoped**。
  el-dialog 默认 teleport 到 body，弹窗内部的 `.el-dialog__body` 由 Element Plus
  自己渲染，不带本组件的 data-v 属性，scoped 选择器一律选不中。
  用 dialog 自己的 class（.knowledge-detail-dialog）做命名空间，避免污染全局版本。
  限高 + 滚动的理由：知识正文长度不一（实测最长 112 字，还可能更长），
  不限高的话弹窗会顶出视口，底部的「内容说明」永远看不到。
-->
<style>
.knowledge-detail-dialog .el-dialog__body {
  max-height: 68vh;
  overflow-y: auto;
}
/* 正文行高偏大（2.0），首尾留白在滚动容器里会显得空，收一点 */
.knowledge-detail-dialog .el-dialog__body > div { padding-bottom: 4px; }
</style>