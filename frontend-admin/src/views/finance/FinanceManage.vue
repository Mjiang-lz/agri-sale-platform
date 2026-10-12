<template>
  <div class="finance-manage-page">
    <!--
      页面用途：管理员查看全平台融资台账（只读）。
      数据来自 GET /api/admin/finance/applies，支持按申请状态筛选、按申请编号或农户姓名搜索。
      审批动作归银行用户（POST /api/loan-admin/applies/{applyId}/approve），管理员调用会 403，
      所以本页不提供任何审批按钮，只做核对与追溯。
    -->
    <section class="finance-hero">
      <div>
        <span class="eyebrow">M6 · 平台治理</span>
        <h2>融资管理</h2>
        <p>管理员只读台账：核对谁申请了什么额度、流转到哪一步、银行出了什么结论。审批动作由银行用户完成，本页不提供审批入口</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <span>申请总数</span>
          <strong>{{ formatNumber(distributionTotal) }}</strong>
        </div>
        <el-button class="hero-refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
    </section>

    <!-- 状态漏斗：直接用后端按 6 状态词表统计的分布，不在前端累加分页数据 -->
    <el-card shadow="never" class="funnel-card">
      <template #header>
        <div class="card-head">
          <div><strong>融资流转分布</strong><span>按申请状态词表统计，含 0 条的状态</span></div>
          <span class="card-total">共 <b>{{ formatNumber(distributionTotal) }}</b> 笔</span>
        </div>
      </template>
      <div v-if="distribution.length" class="funnel">
        <button
          v-for="item in distribution"
          :key="item.name"
          type="button"
          class="funnel-step"
          :class="{ active: query.applyStatus === item.name }"
          :title="`筛选「${item.name}」`"
          @click="toggleStatusFilter(item.name)"
        >
          <span class="funnel-name">{{ item.name }}</span>
          <span class="funnel-bar-area">
            <span
              class="funnel-bar"
              :style="{ width: `${funnelWidth(item.count)}%`, background: statusColor(item.name) }"
            />
          </span>
          <span class="funnel-count">{{ formatNumber(item.count) }}</span>
        </button>
      </div>
      <el-empty v-else description="暂无融资申请" :image-size="70" />
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <div class="filters">
            <el-select v-model="query.applyStatus" placeholder="全部状态" clearable class="filter-status" @change="handleStatusChange">
              <el-option v-for="status in FINANCE_STATUSES" :key="status" :label="status" :value="status" />
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="按申请编号或农户姓名搜索"
              clearable
              class="filter-keyword"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
          <span class="record-count">共 {{ total }} 笔申请</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" row-key="applyId" @row-click="openDetail">
        <el-table-column label="申请编号 / 农户" min-width="215">
          <template #default="{ row }">
            <div class="apply-cell">
              <strong>{{ row.applyNo }}</strong>
              <small>{{ row.farmerName || '--' }} · {{ row.farmerMainProduct || '未填主营品类' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="申请额度" width="135" align="right">
          <template #default="{ row }">
            <div class="amount-cell">
              <strong>¥{{ formatMoney(row.applyAmount) }}</strong>
              <small>{{ row.applyTerm }} 个月</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="贷款产品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.loanProductName || '--' }}</template>
        </el-table-column>
        <el-table-column label="申请状态" width="125">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.applyStatus)">{{ row.applyStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="银行结论" width="115">
          <template #default="{ row }">
            <el-tag v-if="row.approvalResult" :type="row.approvalResult === '通过' ? 'success' : 'danger'">
              {{ row.approvalResult }}
            </el-tag>
            <span v-else class="muted">未审批</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有融资申请" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="sizes, prev, pager, next"
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="融资申请详情" size="min(720px, 96%)">
      <template v-if="detail">
        <div class="detail-head">
          <div>
            <span>申请编号 {{ detail.applyNo }}</span>
            <h3>{{ detail.farmerName }} · {{ formatMoney(detail.applyAmount) }} 元 / {{ detail.applyTerm }} 个月</h3>
          </div>
          <el-tag :type="statusTagType(detail.applyStatus)" size="large">{{ detail.applyStatus }}</el-tag>
        </div>

        <!-- 流转时间线：把三个关键时间点按流转顺序串起来，卡在哪一步一眼可见 -->
        <el-steps :active="flowStep(detail.applyStatus)" align-center finish-status="success" class="flow-steps">
          <el-step title="提交申请" :description="formatDateTime(detail.applyTime) || '--'" />
          <el-step title="推送银行" :description="formatDateTime(detail.pushTime) || '未推送'" />
          <el-step
            title="银行审批"
            :description="detail.approvalTime ? formatDateTime(detail.approvalTime) : '未审批'"
          />
          <el-step title="放款" :description="detail.applyStatus === '已放款' ? '已完成' : '未放款'" />
        </el-steps>

        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="申请农户">{{ detail.farmerName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.farmerPhone || '--' }}</el-descriptions-item>
          <el-descriptions-item label="主营品类">{{ detail.farmerMainProduct || '--' }}</el-descriptions-item>
          <el-descriptions-item label="贷款产品">{{ detail.loanProductName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="申请额度">{{ formatMoney(detail.applyAmount) }} 元</el-descriptions-item>
          <el-descriptions-item label="申请期限">{{ detail.applyTerm }} 个月</el-descriptions-item>
          <el-descriptions-item label="资金用途" :span="2">
            <div class="full-content">{{ detail.applyPurpose || '未填写' }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <div class="approval-block" :class="{ rejected: detail.approvalResult === '不通过' }">
          <h4>银行审批结论</h4>
          <template v-if="detail.approvalResult">
            <div class="approval-head">
              <el-tag :type="detail.approvalResult === '通过' ? 'success' : 'danger'" size="large">
                {{ detail.approvalResult }}
              </el-tag>
              <span v-if="detail.loanAmount">核准放款 {{ formatMoney(detail.loanAmount) }} 元</span>
              <span>审批时间 {{ formatDateTime(detail.approvalTime) }}</span>
            </div>
            <p class="approval-opinion">{{ detail.approvalOpinion || '银行未填写审批意见' }}</p>
          </template>
          <el-empty v-else description="银行尚未审批" :image-size="60" />
        </div>

        <el-alert
          class="readonly-alert"
          type="info"
          show-icon
          :closable="false"
          title="管理员只读"
          description="审批结论由银行用户在银行端给出，管理员在此只能查看，不能修改。若需推进流程，请联系对应银行用户处理"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getAdminFinanceApplies, getFinanceStatusDistribution } from '@/api/finance'

/**
 * 申请状态词表，与后端 AdminDashboardServiceImpl.FINANCE_STATUSES 保持一致，
 * 顺序即漏斗从前往后的流转顺序。数量为 0 的状态也要显示 ——
 * 隐藏空状态会让「待匹配 0 笔」看起来像功能缺失。
 */
const FINANCE_STATUSES = ['待匹配', '已组建小组', '已推送银行', '审批通过', '审批未通过', '已放款']

/** 状态配色：由浅到深表示流转推进，审批未通过单独标红。 */
const STATUS_COLORS = {
  待匹配: '#b8ccc2',
  已组建小组: '#8fb8a4',
  已推送银行: '#5b9c7c',
  审批通过: '#2f8a5b',
  审批未通过: '#c9564e',
  已放款: '#1d5637'
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const distribution = ref([])
const detailVisible = ref(false)
const detail = ref(null)

const query = reactive({
  applyStatus: null,
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

/** 漏斗合计：来自后端分布统计，不是当前页累加。 */
const distributionTotal = computed(() =>
  distribution.value.reduce((sum, item) => sum + Number(item.count || 0), 0)
)
const distributionMax = computed(() => Math.max(1, ...distribution.value.map((item) => Number(item.count || 0))))

/** 漏斗条按最大值的比例出宽度；数量为 0 时给一个最小可见宽度。 */
function funnelWidth(count) {
  const value = Number(count || 0)
  if (!value) return 2
  return Math.max(8, Math.round((value / distributionMax.value) * 100))
}

/** 点当前已选中的状态行 = 取消筛选，回到全量。 */
function toggleStatusFilter(status) {
  query.applyStatus = query.applyStatus === status ? null : status
  handleSearch()
}

function statusColor(status) {
  return STATUS_COLORS[status] || '#b8ccc2'
}

function statusTagType(status) {
  if (status === '审批未通过') return 'danger'
  if (status === '已放款' || status === '审批通过') return 'success'
  if (status === '已推送银行') return 'warning'
  return 'info'
}

/** 流转时间线走到第几步：未推送=1，已推送未审批=2，出结论=3，已放款=4。 */
function flowStep(status) {
  if (status === '已放款') return 4
  if (status === '审批通过' || status === '审批未通过') return 3
  if (status === '已推送银行') return 2
  return 1
}

function formatMoney(value) {
  const number = Number(value || 0)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2,
    maximumFractionDigits: 2
  }).format(number)
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

async function loadList() {
  loading.value = true
  try {
    const data = await getAdminFinanceApplies({
      applyStatus: query.applyStatus || undefined,
      keyword: query.keyword?.trim() || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** 漏斗分布取自数据看板接口；失败不阻塞台账本身。 */
async function loadDistribution() {
  try {
    const data = await getFinanceStatusDistribution()
    distribution.value = data?.financeStatusDistribution || []
  } catch {
    distribution.value = []
  }
}

async function refreshAll() {
  await Promise.all([loadList(), loadDistribution()])
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleStatusChange() {
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.applyStatus = null
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

onMounted(refreshAll)
</script>

<style scoped>
.finance-manage-page { color: #24332b; }
.finance-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #173f2a, #2b8154); box-shadow: 0 16px 34px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }
.finance-hero h2 { margin: 9px 0 7px; font-size: 26px; white-space: nowrap; }
.finance-hero p { margin: 0; max-width: 740px; color: rgba(255,255,255,.72); line-height: 1.7; }
.finance-hero > div:first-child { flex: 1; min-width: 0; }
.hero-side { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.hero-stat { padding: 12px 18px; text-align: right; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-stat span { display: block; color: rgba(255,255,255,.66); font-size: 12px; }
.hero-stat strong { display: block; margin-top: 5px; font-size: 22px; }
.hero-refresh { color: #173f2a; font-weight: 600; background: #ccebd5; border-color: transparent; }
.hero-refresh:hover { color: #173f2a; background: #e3f5ea; border-color: transparent; }
.funnel-card { margin: 18px 0; border-radius: 15px; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.card-head strong { display: block; font-size: 15px; }
.card-head span { margin-top: 4px; color: #75827a; font-size: 12px; }
.card-total b { color: #1d5637; }
.funnel { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.funnel-step { display: flex; flex-direction: column; gap: 8px; padding: 12px; cursor: pointer; border: 1px solid #e1eae4; border-radius: 12px; background: #fff; text-align: left; transition: border-color .18s ease, box-shadow .18s ease; }
.funnel-step:hover { border-color: #9dc4ad; box-shadow: 0 6px 14px rgba(23, 63, 42, .08); }
.funnel-step.active { border-color: #2f8a5b; background: #f2f8f4; box-shadow: 0 0 0 1px #2f8a5b; }
.funnel-name { color: #4b5a51; font-size: 12px; white-space: nowrap; }
.funnel-bar-area { height: 8px; border-radius: 5px; background: #f0f4f1; overflow: hidden; }
.funnel-bar { display: block; height: 100%; border-radius: 5px; transition: width .4s ease; }
.funnel-count { color: #1d5637; font-size: 18px; font-weight: 600; }
.table-card { border-radius: 15px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filter-status { width: 140px; }
.filter-keyword { width: 240px; }
.record-count { color: #75827a; font-size: 12px; }
.table-card :deep(.el-table__row) { cursor: pointer; }
.apply-cell strong, .apply-cell small { display: block; }
.apply-cell small { margin-top: 4px; color: #8b978f; font-size: 12px; }
.amount-cell strong, .amount-cell small { display: block; }
.amount-cell strong { color: #1d5637; font-size: 15px; }
.amount-cell small { margin-top: 3px; color: #8b978f; font-size: 11px; }
.muted { color: #a3aea7; font-size: 12px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 18px; }
.detail-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; }
.detail-head span { color: #75827a; font-size: 12px; }
.detail-head h3 { margin: 5px 0 0; font-size: 18px; }
.flow-steps { margin-bottom: 22px; }
.detail-desc { margin-bottom: 20px; }
.full-content { white-space: pre-wrap; line-height: 1.8; }
.approval-block { padding: 18px; border: 1px solid #e1eae4; border-left: 4px solid #2f8a5b; border-radius: 12px; background: #f6faf7; }
.approval-block.rejected { border-left-color: #c9564e; background: #fdf7f6; }
.approval-block h4 { margin: 0 0 12px; font-size: 14px; }
.approval-head { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; color: #75827a; font-size: 12px; }
.approval-opinion { margin: 12px 0 0; padding-top: 12px; border-top: 1px dashed #e1eae4; color: #4b5a51; line-height: 1.8; white-space: pre-wrap; }
.readonly-alert { margin-top: 18px; }
@media (max-width: 1280px) { .funnel { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1000px) { .finance-hero { align-items: stretch; flex-direction: column; } .hero-side { justify-content: space-between; } .hero-stat { text-align: left; } }
@media (max-width: 680px) { .funnel { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filters { width: 100%; } .filter-status, .filter-keyword { flex: 1; width: auto; min-width: 130px; } .hero-side { flex-direction: column; align-items: stretch; } }
</style>
