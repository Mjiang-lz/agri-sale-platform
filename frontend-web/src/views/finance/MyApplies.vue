<template>
  <div class="my-applies-page">
    <!-- 页面用途：农户查看自己提交过的融资申请和当前审批进度。 -->
    <div class="page-head">
      <div>
        <h2>我的融资</h2>
        <p>查看融资申请、联合小组、银行审批和放款进度。点击“查看进度”可展开完整时间轴</p>
      </div>
      <div class="head-actions">
        <el-tag v-if="demoMode" type="warning" effect="dark">演示数据</el-tag>
        <el-button :loading="loading" @click="loadApplies">刷新进度</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <el-tabs v-model="activeStatus" @tab-change="handleStatusChange">
            <el-tab-pane label="全部" name="全部" />
            <el-tab-pane label="待匹配" name="待匹配" />
            <el-tab-pane label="进行中" name="进行中" />
            <el-tab-pane label="已完成" name="已完成" />
            <el-tab-pane label="未通过" name="未通过" />
          </el-tabs>
          <el-input v-model="keyword" clearable placeholder="搜索申请编号" style="width: 240px" />
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredApplies" row-key="applyId">
        <el-table-column prop="applyNo" label="申请编号" min-width="165" />
        <el-table-column label="贷款产品" min-width="150">
          <template #default="{ row }">{{ row.productName || row.loanProductName || `产品 #${row.loanProductId || '--'}` }}</template>
        </el-table-column>
        <el-table-column label="申请额度" min-width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.applyAmount) }} 元</template>
        </el-table-column>
        <el-table-column label="期限" width="90">
          <template #default="{ row }">{{ row.applyTerm }} 个月</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.applyTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="125">
          <template #default="{ row }">
            <el-tag :type="statusType(row.applyStatus)">{{ row.applyStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看进度</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无融资申请" /></template>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="融资申请进度" size="min(620px, 96%)">
      <template v-if="currentApply">
        <div class="detail-header">
          <div>
            <span>{{ currentApply.applyNo }}</span>
            <h3>{{ currentApply.productName || currentApply.loanProductName || '融资申请' }}</h3>
          </div>
          <el-tag :type="statusType(currentApply.applyStatus)">{{ currentApply.applyStatus }}</el-tag>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请额度">{{ formatMoney(currentApply.applyAmount) }} 元</el-descriptions-item>
          <el-descriptions-item label="申请期限">{{ currentApply.applyTerm }} 个月</el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">{{ formatDateTime(currentApply.applyTime || currentApply.createTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="currentApply.applyPurpose" label="资金用途" :span="2">{{ currentApply.applyPurpose }}</el-descriptions-item>
        </el-descriptions>

        <div class="timeline-title">状态进度时间轴（按申请状态生成）</div>
        <el-timeline>
          <el-timeline-item
            v-for="item in getTimeline(currentApply.applyStatus)"
            :key="item.title"
            :type="item.type"
            :hollow="item.hollow"
            :timestamp="item.time"
          >
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMyFinanceApplies } from '@/api/finance'

/**
 * 页面用途：我的融资。
 * 接口文件：frontend-web/src/api/finance.js
 * 主要接口：GET /api/finance/apply/my
 *
 * 后端 FinanceApplyVO 返回：
 * applyId、applyNo、applyAmount、applyTerm、applyStatus、applyTime。
 * 默认只展示真实接口结果；只有 VITE_USE_DEMO=true 时才允许回退到演示数据。
 */
const useDemo = import.meta.env.VITE_USE_DEMO === 'true'
const demoMode = ref(false)
const loading = ref(false)
const activeStatus = ref('全部')
const keyword = ref('')
const applies = ref([])
const detailVisible = ref(false)
const currentApply = ref(null)

function demoApplies() {
  return [
    { applyId: 1, applyNo: 'RT20260908001', applyAmount: 100000, applyTerm: 24, applyStatus: '已放款', applyTime: '2026-09-08 09:10:00' },
    { applyId: 2, applyNo: 'RT20260908002', applyAmount: 100000, applyTerm: 24, applyStatus: '审批通过', applyTime: '2026-09-08 09:30:00' },
    { applyId: 3, applyNo: 'RT20260908003', applyAmount: 100000, applyTerm: 24, applyStatus: '已推送银行', applyTime: '2026-09-08 10:00:00' },
    { applyId: 4, applyNo: 'RT20260912001', applyAmount: 80000, applyTerm: 36, applyStatus: '已组建小组', applyTime: '2026-09-12 09:20:00' },
    { applyId: 5, applyNo: 'RT20260914001', applyAmount: 70000, applyTerm: 12, applyStatus: '待匹配', applyTime: '2026-09-14 09:00:00' },
    { applyId: 6, applyNo: 'RT20260913001', applyAmount: 50000, applyTerm: 12, applyStatus: '审批未通过', applyTime: '2026-09-13 08:40:00' }
  ]
}

const filteredApplies = computed(() => {
  const key = keyword.value.trim()
  return applies.value.filter((item) => {
    const statusMatch = {
      '全部': true,
      '待匹配': ['待匹配', '待审核'].includes(item.applyStatus),
      '进行中': ['已组建小组', '已推送银行'].includes(item.applyStatus),
      '已完成': ['审批通过', '已放款'].includes(item.applyStatus),
      '未通过': ['审批未通过', '已驳回', '未通过'].includes(item.applyStatus)
    }[activeStatus.value]
    const keywordMatch = !key || String(item.applyNo || '').includes(key)
    return statusMatch && keywordMatch
  })
})

function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

async function loadApplies() {
  loading.value = true
  try {
    const data = await getMyFinanceApplies(1, 100)
    applies.value = normalizeList(data)
    if (!applies.value.length && useDemo) {
      applies.value = demoApplies()
      demoMode.value = true
    }
  } catch {
    // 默认保留真实错误状态；只有显式开启演示模式才回退，避免掩盖接口故障。
    applies.value = useDemo ? demoApplies() : []
    if (useDemo) demoMode.value = true
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  detailVisible.value = false
}

function openDetail(row) {
  currentApply.value = row
  detailVisible.value = true
}

function statusType(status) {
  if (['审批通过', '已放款', '已通过'].includes(status)) return 'success'
  if (['审批未通过', '已驳回', '未通过'].includes(status)) return 'danger'
  if (['待匹配', '待审核', '已组建小组', '已推送银行'].includes(status)) return 'warning'
  return 'info'
}

function getTimeline(status) {
  const stepIndex = {
    '待匹配': 1,
    '待审核': 1,
    '已组建小组': 2,
    '已推送银行': 3,
    '审批通过': 4,
    '已放款': 5,
    '审批未通过': 4,
    '已驳回': 4,
    '未通过': 4
  }[status] || 1
  const failed = ['审批未通过', '已驳回', '未通过'].includes(status)
  const steps = [
    ['提交融资申请', '申请信息已保存'],
    ['智能匹配联保人', '完成联保候选人匹配与小组组建'],
    ['推送银行审批', '融资申请已进入银行侧工作台'],
    [failed ? '银行审批未通过' : '银行审批通过', failed ? '请根据审批意见调整后重新申请' : '银行已给出审批结论'],
    ['放款完成', '核准资金已发放到申请人账户']
  ]
  return steps.map(([title, description], index) => ({
    title,
    description,
    type: index + 1 < stepIndex ? 'success' : index + 1 === stepIndex ? (failed ? 'danger' : 'primary') : 'info',
    hollow: index + 1 > stepIndex,
    time: index + 1 < stepIndex ? '已完成' : index + 1 === stepIndex ? '当前节点' : '等待中'
  }))
}

function formatMoney(value) {
  const number = Number(value || 0)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2,
    maximumFractionDigits: 2
  }).format(number)
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(loadApplies)
</script>

<style scoped>
.table-card { border-radius: 14px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 18px; }
.toolbar :deep(.el-tabs__header) { margin: 0; }
.detail-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 18px; padding: 15px; border-radius: 12px; background: var(--el-color-primary-light-9); }
.detail-header span { color: var(--el-text-color-secondary); font-size: 12px; }
.detail-header h3 { margin: 5px 0 0; }
.timeline-title { margin: 24px 0 15px; font-weight: 600; }
.timeline-title + .el-timeline p { margin: 6px 0 0; color: var(--el-text-color-secondary); line-height: 1.6; }
@media (max-width: 760px) { .page-head, .toolbar { flex-direction: column; align-items: stretch; } .toolbar .el-input { width: 100% !important; } }
</style>