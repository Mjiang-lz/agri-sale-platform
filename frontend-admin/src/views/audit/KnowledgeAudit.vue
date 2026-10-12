<template>
  <div class="knowledge-audit-page">
    <!--
      页面用途：平台管理员审核专家提交的农业知识。
      默认展示待审核队列，可查看完整正文，通过后进入前台知识列表，驳回必须填写原因。
    -->
    <section class="audit-hero">
      <div>
        <span class="eyebrow">M6 · 农技内容治理</span>
        <h2>知识审核</h2>
        <p>检查农技文章的内容合规性与专业性，通过后公开展示，驳回时向专家反馈修改原因</p>
      </div>
      <div class="hero-status">
        <span>当前待办</span>
        <strong>{{ pendingTotal }}</strong>
        <small>篇知识</small>
      </div>
    </section>

    <section class="stat-grid">
      <article class="stat-card"><span>待审核</span><strong>{{ pendingTotal }}</strong><small>待处理知识量</small></article>
      <article class="stat-card"><span>今日已审</span><strong>{{ todayReviewedTotal }}</strong><small>通过 + 驳回</small></article>
      <article class="stat-card"><span>我的累计已审</span><strong>{{ statistics.myReviewedTotal || 0 }}</strong><small>当前审核员</small></article>
      <article class="stat-card"><span>近 7 日驳回率</span><strong>{{ recentRejectionRate }}</strong><small>仅知识审核</small></article>
    </section>

    <el-card shadow="never" class="trend-card">
      <template #header>
        <div class="section-head">
          <div><strong>近 7 日审核趋势</strong><span>绿色为通过，橙色为驳回</span></div>
          <span class="trend-summary">共 {{ recentReviewedTotal }} 条审核动作</span>
        </div>
      </template>
      <div class="trend-list">
        <div v-for="item in statistics.last7Days || []" :key="item.date" class="trend-item">
          <div class="bar-area">
            <span class="bar reviewed" :style="{ height: `${barHeight(item.reviewed + item.rejected)}%` }" />
            <span v-if="item.rejected" class="rejected-dot" :title="`驳回 ${item.rejected} 条`" />
          </div>
          <strong>{{ item.reviewed + item.rejected }}</strong>
          <small>{{ item.date.slice(5) }}</small>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <el-radio-group v-model="activeStatus" @change="handleStatusChange">
            <el-radio-button value="待审核">待审核</el-radio-button>
            <el-radio-button value="已通过">已通过</el-radio-button>
            <el-radio-button value="未通过">未通过</el-radio-button>
          </el-radio-group>
          <div class="toolbar-actions">
            <span class="record-count">共 {{ total }} 条</span>
            <el-button :loading="loading" @click="refreshAll">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" :row-key="rowKey" @row-click="openDetail">
        <el-table-column prop="targetTitle" label="知识标题" min-width="260" show-overflow-tooltip />
        <el-table-column prop="submitterName" label="发布专家" width="130">
          <template #default="{ row }">{{ row.submitterName || `专家 #${row.submitterId || '--'}` }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.auditStatus)">{{ row.auditStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="auditRemark" label="审核意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看详情</el-button>
            <template v-if="row.auditStatus === '待审核'">
              <el-button link type="success" @click.stop="approve(row)">通过</el-button>
              <el-button link type="danger" @click.stop="openReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有知识内容" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="sizes, prev, pager, next"
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="知识审核详情" size="min(720px, 96%)">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-head">
            <div><span>知识内容 #{{ detail.targetId }}</span><h3>{{ detail.targetTitle }}</h3></div>
            <el-tag :type="statusType(detail.auditStatus)" size="large">{{ detail.auditStatus }}</el-tag>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="发布专家">{{ detail.submitterName || `专家 #${detail.submitterId || '--'}` }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submitTime) }}</el-descriptions-item>
            <el-descriptions-item label="知识正文">
              <div class="full-content">{{ detail.content || '暂无知识正文' }}</div>
            </el-descriptions-item>
            <!--
              配图必须给审核员看到：一条知识正文写得再合规，配图也可能是违规内容。
              imageUrl 由后端详情接口返回（列表接口不带，所以打开抽屉时会有极短的空白期）。

              fit 用 contain 而不是 cover：专家上传的可能是竖图（实测这张是 810x1440），
              cover 会把竖图裁成中间一条 —— 审核场景下"看不到的部分"恰恰可能是有问题的部分。
              contain 会留白，但保证整张图都在视野里；细节靠点击放大看。
            -->
            <el-descriptions-item label="知识配图">
              <div v-if="detail.imageUrl" class="image-cell">
                <el-image
                  class="content-image"
                  :src="detail.imageUrl"
                  :preview-src-list="[detail.imageUrl]"
                  preview-teleported
                  fit="contain"
                />
                <span class="image-tip">点击图片可放大查看</span>
              </div>
              <span v-else class="no-image">该知识未上传配图</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.auditResult" label="审核结论">{{ detail.auditResult }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.auditRemark" label="审核意见">{{ detail.auditRemark }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.auditorName" label="审核人">{{ detail.auditorName }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.auditTime" label="审核时间">{{ formatDateTime(detail.auditTime) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="detail?.auditStatus === '待审核'">
          <el-button type="danger" @click="openReject(detail); detailVisible = false">驳回</el-button>
          <el-button type="success" @click="approve(detail); detailVisible = false">审核通过</el-button>
        </template>
      </template>
    </el-drawer>

    <el-dialog v-model="rejectVisible" title="填写知识驳回理由" width="min(540px, 92vw)">
      <p class="reject-tip">理由会写入 t_audit.audit_remark，并反馈给知识发布专家</p>
      <el-input v-model="rejectRemark" type="textarea" :rows="5" maxlength="200" show-word-limit placeholder="例如：文中防治方法缺少科学依据，请补充来源和适用条件" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgeAuditContent,
  getKnowledgeAuditList,
  getKnowledgeAuditStatistics,
  getKnowledgePendingList,
  submitKnowledgeAudit
} from '@/api/audit'

/** 知识审核页状态。 */
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const detailVisible = ref(false)
const rejectVisible = ref(false)
const rows = ref([])
const detail = ref(null)
const rejectTarget = ref(null)
const rejectRemark = ref('')
const activeStatus = ref('待审核')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const pendingTotal = ref(0)
const statistics = ref({ myReviewedTotal: 0, last7Days: [] })

const recentReviewedTotal = computed(() =>
  (statistics.value.last7Days || []).reduce((sum, item) => sum + Number(item.reviewed || 0) + Number(item.rejected || 0), 0)
)
const recentRejectedTotal = computed(() =>
  (statistics.value.last7Days || []).reduce((sum, item) => sum + Number(item.rejected || 0), 0)
)
const recentRejectionRate = computed(() =>
  recentReviewedTotal.value ? `${Math.round(recentRejectedTotal.value / recentReviewedTotal.value * 100)}%` : '0%'
)
const todayReviewedTotal = computed(() => {
  const last = (statistics.value.last7Days || []).at(-1)
  return last ? Number(last.reviewed || 0) + Number(last.rejected || 0) : 0
})
const maxTrendValue = computed(() =>
  Math.max(1, ...(statistics.value.last7Days || []).map((item) => Number(item.reviewed || 0) + Number(item.rejected || 0)))
)

function rowKey(row) {
  return `${row.targetType}-${row.targetId}`
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

function statusType(status) {
  if (status === '已通过') return 'success'
  if (status === '未通过') return 'danger'
  return 'warning'
}

function barHeight(value) {
  return Math.max(8, Math.round(Number(value || 0) / maxTrendValue.value * 100))
}

async function loadList() {
  loading.value = true
  try {
    const data = activeStatus.value === '待审核'
      ? await getKnowledgePendingList(pageNum.value, pageSize.value)
      : await getKnowledgeAuditList(activeStatus.value, pageNum.value, pageSize.value)
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
    if (activeStatus.value === '待审核') pendingTotal.value = total.value
  } finally {
    loading.value = false
  }
}

async function loadStatistics() {
  const [stats, pending] = await Promise.all([
    getKnowledgeAuditStatistics(),
    getKnowledgePendingList(1, 1)
  ])
  statistics.value = stats || { myReviewedTotal: 0, last7Days: [] }
  pendingTotal.value = Number(pending?.total || 0)
}

async function refreshAll() {
  await Promise.all([loadList(), loadStatistics()])
}

function handleStatusChange() {
  pageNum.value = 1
  loadList()
}

function handleSizeChange() {
  pageNum.value = 1
  loadList()
}

async function openDetail(row) {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const data = await getKnowledgeAuditContent(row.targetId)
    if (data) detail.value = data
  } finally {
    detailLoading.value = false
  }
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过知识“${row.targetTitle}”吗？通过后将在前台农业知识页展示`, '审核确认', {
    type: 'warning',
    confirmButtonText: '确认通过',
    cancelButtonText: '取消'
  })
  await submitKnowledgeAudit(row.targetId, { auditResult: '通过' })
  ElMessage.success('知识审核已通过')
  await refreshAll()
}

function openReject(row) {
  rejectTarget.value = row
  rejectRemark.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  const remark = rejectRemark.value.trim()
  if (!remark) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  submitting.value = true
  try {
    await submitKnowledgeAudit(rejectTarget.value.targetId, {
      auditResult: '不通过',
      auditRemark: remark
    })
    rejectVisible.value = false
    ElMessage.success('知识内容已驳回')
    await refreshAll()
  } finally {
    submitting.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.knowledge-audit-page { color: #24332b; }
.audit-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #17434a, #287f7b); box-shadow: 0 16px 34px rgba(23, 67, 74, .16); }
.eyebrow { color: #d2f0ec; font-size: 13px; }
.audit-hero h2 { margin: 9px 0 7px; font-size: 26px; }
.audit-hero p { margin: 0; max-width: 760px; color: rgba(255,255,255,.75); line-height: 1.7; }
.hero-status { min-width: 130px; padding: 14px 20px; text-align: center; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-status span, .hero-status small { display: block; color: rgba(255,255,255,.68); }
.hero-status strong { display: block; margin: 4px 0; font-size: 28px; }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.stat-card { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.stat-card span, .stat-card small { display: block; color: #75827a; }
.stat-card strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #226b68; }
.stat-card small { font-size: 11px; }
.trend-card, .table-card { border-radius: 15px; }
.trend-card { margin-bottom: 18px; }
.section-head, .toolbar, .toolbar-actions, .detail-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.section-head strong, .section-head span { display: block; }
.section-head > div > span, .trend-summary, .record-count { margin-top: 4px; color: #75827a; font-size: 12px; }
.trend-list { height: 150px; display: grid; grid-template-columns: repeat(7, 1fr); gap: 12px; align-items: end; }
.trend-item { text-align: center; }
.bar-area { position: relative; height: 100px; display: flex; align-items: flex-end; justify-content: center; }
.bar { width: 26px; min-height: 8px; border-radius: 7px 7px 3px 3px; background: linear-gradient(180deg, #61bfb2, #287f7b); }
.rejected-dot { position: absolute; right: calc(50% - 18px); bottom: 3px; width: 10px; height: 10px; border-radius: 50%; background: #df8a32; box-shadow: 0 0 0 2px #fff; }
.trend-item strong { display: block; margin-top: 6px; font-size: 13px; }
.trend-item small { color: #839087; font-size: 11px; }
.table-card :deep(.el-table__row) { cursor: pointer; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 18px; }
.detail-head { margin-bottom: 18px; }
.detail-head span { color: #75827a; font-size: 12px; }
.detail-head h3 { margin: 5px 0 0; }
.full-content { white-space: pre-wrap; line-height: 1.8; }
.image-cell { display: flex; flex-direction: column; gap: 6px; }
/* 260x220 + contain：竖图/横图都能整张看到，浅底让留白看着是有意为之 */
.content-image { width: 260px; height: 220px; border: 1px solid #e1eae4; border-radius: 10px; background: #f7faf8; cursor: zoom-in; }
.image-tip { color: #98a49c; font-size: 12px; }
.no-image { color: #98a49c; }
.reject-tip { margin: 0 0 12px; color: #75827a; line-height: 1.7; font-size: 13px; }
@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) { .audit-hero, .toolbar, .section-head { align-items: stretch; flex-direction: column; } .hero-status { width: 100%; } .stat-grid { grid-template-columns: 1fr; } .trend-list { gap: 5px; } .toolbar-actions { justify-content: space-between; } }
</style>
