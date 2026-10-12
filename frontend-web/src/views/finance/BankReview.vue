<template>
  <div class="bank-review-page">
    <div class="page-head">
      <div>
        <h2>融资审批</h2>
        <p>这里是已推送至银行侧的融资申请。查看农户信息与资金用途后给出审批结论，结论会实时回传到农户的「我的融资」</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadList">刷新</el-button>
      </div>
    </div>

    <!-- 概览：数字来自同一个列表，不额外打接口 -->
    <div class="stat-row">
      <div v-for="s in stats" :key="s.key" class="stat-card" :class="s.tone">
        <span class="st-num">{{ s.value }}</span>
        <span class="st-label">{{ s.label }}</span>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="filter-bar">
          <el-radio-group v-model="statusFilter">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="pending">待我审批</el-radio-button>
            <el-radio-button value="approved">已通过</el-radio-button>
            <el-radio-button value="rejected">未通过</el-radio-button>
          </el-radio-group>
        <span class="filter-hint">共 {{ rows.length }} 笔</span>
      </div>

      <el-alert
        v-if="errorState"
        type="error"
        show-icon
        :closable="false"
        title="融资申请加载失败"
        class="error-alert"
      >
        <template #default>
          <div class="error-desc">无法连接后端服务，或当前账号不是银行用户角色（本接口仅银行用户可用）</div>
          <div class="error-actions">
            <el-button type="danger" size="small" @click="loadList">重新加载</el-button>
          </div>
        </template>
      </el-alert>

      <el-table v-else v-loading="loading" :data="rows" row-key="applyId">
        <el-table-column prop="applyNo" label="申请编号" min-width="168" />

        <el-table-column label="申请农户" min-width="150">
          <template #default="{ row }">
            <div class="farmer-cell">
              <strong>{{ row.farmerName || `农户 #${row.farmerId}` }}</strong>
              <!-- 手机号是「联系核实」用的，按需求 §2.9 银行侧必须能看到 -->
              <small>{{ row.farmerPhone || '未留电话' }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="主营农产品" min-width="130">
          <template #default="{ row }">
            <span :class="{ 'muted': !row.farmerMainProduct }">{{ row.farmerMainProduct || '未填写' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="申请额度" width="130" align="right">
          <template #default="{ row }">
            <strong class="money">¥{{ formatAmount(row.applyAmount) }}</strong>
          </template>
        </el-table-column>

        <el-table-column label="期限" width="90" align="right">
          <template #default="{ row }">{{ row.applyTerm ?? '—' }} 个月</template>
        </el-table-column>

        <el-table-column label="贷款产品" min-width="130">
          <template #default="{ row }">{{ row.loanProductName || '—' }}</template>
        </el-table-column>

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.applyStatus)">{{ row.applyStatus }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="推送时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.pushTime) || '—' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
            <!--
              只有「已推送银行」的申请能审批。后端拒绝重复审批
              （会报「该申请当前状态为「审批通过」，不可审批」），
              所以已出结论的走「查看详情」看当初的结论，不再给审批入口。
            -->
            <el-button
              v-if="isPendingApply(row.applyStatus)"
              link
              type="success"
              @click="openApprove(row)"
            >
              审批
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty :description="statusFilter ? '该状态下没有申请' : '暂无已推送至银行的融资申请'">
            <el-button v-if="statusFilter" @click="statusFilter = ''">查看全部</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <!-- ============ 详情 ============ -->
    <el-drawer v-model="detailVisible" title="融资申请详情" size="min(620px, 96%)">
      <template v-if="current">
        <div class="detail-head">
          <div>
            <span class="detail-no">{{ current.applyNo }}</span>
            <h3>{{ current.farmerName || `农户 #${current.farmerId}` }} 的融资申请</h3>
          </div>
          <el-tag :type="statusType(current.applyStatus)">{{ current.applyStatus }}</el-tag>
        </div>

        <h4 class="block-title">申请信息</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请额度">
            <strong class="money">¥{{ formatAmount(current.applyAmount) }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="申请期限">{{ current.applyTerm ?? '—' }} 个月</el-descriptions-item>
          <el-descriptions-item label="贷款产品">{{ current.loanProductName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="资金用途">{{ current.applyPurpose || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(current.applyTime) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="推送时间">{{ formatDateTime(current.pushTime) || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">申请人信息</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="农户姓名">{{ current.farmerName || `农户 #${current.farmerId}` }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ current.farmerPhone || '未留电话' }}</el-descriptions-item>
          <el-descriptions-item label="主营农产品">{{ current.farmerMainProduct || '未填写经营档案' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 已出结论时展示审批结果。未审批的申请这几项都是 null，整块跳过 -->
        <template v-if="current.approvalResult">
          <h4 class="block-title">审批结论</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="结论">
              <el-tag :type="current.approvalResult === '通过' ? 'success' : 'danger'">
                {{ current.approvalResult }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="current.loanAmount != null" label="核准放款金额">
              <strong class="money">¥{{ formatAmount(current.loanAmount) }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="审批意见">{{ current.approvalOpinion || '—' }}</el-descriptions-item>
            <el-descriptions-item label="审批时间">{{ formatDateTime(current.approvalTime) || '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <div class="drawer-foot">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="isPendingApply(current.applyStatus)"
            type="primary"
            @click="openApprove(current); detailVisible = false"
          >
            给出审批结论
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- ============ 审批 ============ -->
    <el-dialog v-model="approveVisible" title="给出审批结论" width="min(560px, 94vw)" destroy-on-close>
      <div v-if="current" class="approve-summary">
        <div class="as-row"><span>申请编号</span><span>{{ current.applyNo }}</span></div>
        <div class="as-row"><span>申请农户</span><span>{{ current.farmerName || `农户 #${current.farmerId}` }}</span></div>
        <div class="as-row"><span>申请额度</span><span class="money">¥{{ formatAmount(current.applyAmount) }}</span></div>
      </div>

      <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="96px" @submit.prevent>
        <el-form-item label="审批结论" prop="approvalResult">
          <el-radio-group v-model="approveForm.approvalResult">
            <el-radio-button value="通过">通过</el-radio-button>
            <el-radio-button value="不通过">不通过</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="approveForm.approvalResult === '通过'" label="核准放款" prop="loanAmount">
          <el-input-number
            v-model="approveForm.loanAmount"
            :min="0.01"
            :max="maxLoanAmount"
            :precision="2"
            :step="10000"
            style="width: 100%"
          />
          <span class="field-tip">不得超过申请额度 ¥{{ formatAmount(current?.applyAmount) }}</span>
        </el-form-item>

        <el-form-item v-else label="审批意见" prop="approvalOpinion">
          <el-input
            v-model="approveForm.approvalOpinion"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请说明不通过的原因，申请人会看到这段文字"
          />
        </el-form-item>
      </el-form>

      <p class="approve-warn">
        结论提交后不可修改 —— 同一笔申请只能审批一次，农户侧「我的融资」会立即更新为最终状态
      </p>

      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApprove">提交结论</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveBankApply, getBankApplies } from '@/api/finance'
import { formatDateTime } from '@/utils/text'
import { APPLY_STATUS, isApprovedApply, isPendingApply } from '@/constants/finance'

/**
 * 页面用途：融资审批（银行用户视角）。
 *
 * ★ 这一页补的是「银行角色进系统后无事可做」的缺口。
 *   银行用户的 menuPerms 里有 finance，但业务端此前**没有银行专用页面** ——
 *   导航把他导向 /finance/my（我的融资），那是「申请人看自己提交的申请」的口径，
 *   银行用户看永远是空列表。
 *
 * 接口文件：frontend-web/src/api/finance.js
 *   GET  /api/loan-admin/applies?status=            已推送至银行侧的申请
 *   POST /api/loan-admin/applies/{id}/approve       提交审批结论
 *
 * ⚠️ 两个与其它列表页不同的地方：
 *   1. 这两个接口在后端是 requireRole(BANK)，其它角色调会拿到
 *      HTTP 200 + body code 403（不是 HTTP 403）。
 *   2. /loan-admin/applies 返回的是**数组**，不是 MyBatis-Plus 分页对象 ——
 *      银行侧待办天然量少，后端没做分页。所以这里也**不做前端分页**，
 *      而是把全量拉回来后在本地按状态筛选：
 *      这样「全部 / 待审批 / 已通过 / 未通过」四个 tab 的计数与表格数据
 *      来自同一次请求、同一份快照，不会出现「计数说 3 笔、点进去只有 2 笔」
 *      这种由两次请求之间的数据变化造成的错位。
 *
 * 审批规则（后端在 LoanAdminServiceImpl 里裁定，前端只是提前拦一遍）：
 *   · 通过   → 放款金额必填，且不得超过申请额度；
 *   · 不通过 → 审批意见必填；
 *   · 只允许对状态仍为「已推送银行」的申请出结论（防重复审批）。
 */

const loading = ref(false)
const errorState = ref(false)
/** 全量已推送申请（唯一数据源，不做分页） */
const all = ref([])
const statusFilter = ref('')

const detailVisible = ref(false)
const approveVisible = ref(false)
const current = ref(null)
const submitting = ref(false)
const approveFormRef = ref(null)

const approveForm = reactive({
  approvalResult: '通过',
  loanAmount: null,
  approvalOpinion: ''
})

/**
 * tab key → 判定函数。tab 的 value 故意不用后端状态字面量：
 * 「已通过」对应「审批通过」**和**「已放款」两个状态（后端审批通过时
 * 实际写入的是「已放款」，详见 constants/finance.js），一个字符串表达不了。
 * 三个统计格与四个 tab 共用这套判定，保证「格子里的数字 = 点进去的行数」。
 */
const FILTERS = {
  pending: (a) => isPendingApply(a.applyStatus),
  approved: (a) => isApprovedApply(a.applyStatus),
  rejected: (a) => a.applyStatus === APPLY_STATUS.REJECTED
}

/** 本地按状态筛选：一次请求喂满四个 tab 的计数与列表 */
const rows = computed(() => {
  const hit = FILTERS[statusFilter.value]
  return hit ? all.value.filter(hit) : all.value
})

const stats = computed(() => [
  { key: 'pending', label: '待我审批', value: all.value.filter(FILTERS.pending).length, tone: 'is-warn' },
  { key: 'pass', label: '已通过', value: all.value.filter(FILTERS.approved).length, tone: 'is-ok' },
  { key: 'reject', label: '未通过', value: all.value.filter(FILTERS.rejected).length, tone: 'is-bad' }
])

/** 放款金额上限 = 申请额度。后端会再校验一次，这里只是别让用户白填 */
const maxLoanAmount = computed(() => Number(current.value?.applyAmount ?? 0))

const approveRules = {
  approvalResult: [{ required: true, message: '请选择审批结论', trigger: 'change' }],
  loanAmount: [
    {
      required: true,
      message: '审批通过时必须填写放款金额',
      trigger: 'blur'
    },
    {
      validator: (rule, value, callback) => {
        if (value == null) return callback()
        if (Number(value) <= 0) return callback(new Error('放款金额必须大于 0'))
        if (Number(value) > maxLoanAmount.value) {
          return callback(new Error(`核准放款金额不得超过申请额度 ¥${formatAmount(maxLoanAmount.value)}`))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  approvalOpinion: [
    {
      required: true,
      message: '审批不通过时必须填写审批意见',
      trigger: 'blur'
    }
  ]
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  errorState.value = false
  try {
    // 不传 status：一次拿全量，四个 tab 的计数与列表共用同一份数据
    all.value = (await getBankApplies()) || []
    // 列表刷新后抽屉/弹窗里的对象可能是过期的（状态已被审批过），
    // 用 applyId 重新挂到新数组里的那个对象上
    syncCurrent()
  } catch (err) {
    all.value = []
    errorState.value = true
    console.error('融资申请加载失败：', err)
  } finally {
    loading.value = false
  }
}

function syncCurrent() {
  if (!current.value) return
  const latest = all.value.find((a) => a.applyId === current.value.applyId)
  if (latest) current.value = latest
}

function statusType(status) {
  // 「已放款」与「审批通过」是同一个结论，都得是绿的 ——
  // 否则银行刚批完的单子会显示成灰色的「已放款」，看着像没批。
  if (isApprovedApply(status)) return 'success'
  if (status === APPLY_STATUS.REJECTED) return 'danger'
  if (status === APPLY_STATUS.PUSHED) return 'warning'
  return 'info'
}

function formatAmount(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0.00'
  // 千分位：额度动辄 5~6 位，不分隔很难一眼读出量级
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

function openApprove(row) {
  current.value = row
  approveForm.approvalResult = '通过'
  // 默认把申请额度填进去：大多数通过是「按申请额度核准」，
  // 预填省掉一次手工输入，要核减再改
  approveForm.loanAmount = Number(row.applyAmount ?? 0)
  approveForm.approvalOpinion = ''
  approveFormRef.value?.clearValidate()
  approveVisible.value = true
}

async function submitApprove() {
  const valid = await approveFormRef.value?.validate().catch(() => false)
  if (!valid || !current.value) return

  const pass = approveForm.approvalResult === '通过'

  // 结论不可撤回，给一次显式确认
  try {
    await ElMessageBox.confirm(
      pass
        ? `确认核准放款 ¥${formatAmount(approveForm.loanAmount)}？提交后不可修改`
        : '确认给出「不通过」结论？申请人会看到你填写的审批意见，提交后不可修改',
      pass ? '确认审批通过' : '确认审批不通过',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '再想想' }
    )
  } catch {
    return // 用户取消
  }

  submitting.value = true
  try {
    await approveBankApply(current.value.applyId, {
      approvalResult: approveForm.approvalResult,
      // 通过才发放款金额与意见？意见两种结论都可以填，这里统一带上；
      // 不通过时金额传 undefined（后端在 pass=false 时忽略它）
      loanAmount: pass ? approveForm.loanAmount : undefined,
      approvalOpinion: approveForm.approvalOpinion || undefined
    })
    ElMessage.success(pass ? '已核准放款，农户侧状态已更新' : '已给出不通过结论')
    approveVisible.value = false
    await loadList()
  } catch (err) {
    // 失败保持弹窗打开，输入不丢；错误提示已由拦截器给出。
    // 最常见的失败是「该申请已被审批」（别的标签页操作过），
    // 所以顺带刷一次列表让用户看到最新状态
    console.error('提交审批结论失败：', err)
    await loadList()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.bank-review-page {
  color: var(--el-text-color-primary);
}

/* ===== 概览三联 ===== */
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}
.stat-card {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 16px 20px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-fill-color-blank);
  box-shadow: var(--rxt-shadow-sm);
}
.st-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.st-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
/* 待审批用琥珀色（需要动作），已通过用品牌绿，未通过用中性灰 ——
   不用红色：未通过是正常业务结论，不是错误状态。 */
.stat-card.is-warn .st-num { color: var(--rxt-accent); }
.stat-card.is-ok .st-num { color: var(--rxt-primary); }
.stat-card.is-bad .st-num { color: var(--el-text-color-regular); }

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}
.filter-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.error-alert {
  margin-bottom: 8px;
}

.farmer-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.farmer-cell small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.money {
  color: var(--rxt-accent-text, #b26a00);
  font-variant-numeric: tabular-nums;
}
.muted {
  color: var(--el-text-color-secondary);
}

/* ===== 详情抽屉 ===== */
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 18px;
}
.detail-no {
  color: var(--el-text-color-secondary);
  font-family: var(--font-mono, monospace);
  font-size: 12px;
}
.detail-head h3 {
  margin: 6px 0 0;
  font-size: 18px;
}
.block-title {
  margin: 22px 0 10px;
  font-size: 14px;
  font-weight: 600;
}
.block-title:first-of-type {
  margin-top: 0;
}
.drawer-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

/* ===== 审批弹窗 ===== */
.approve-summary {
  padding: 12px 14px;
  margin-bottom: 18px;
  border-radius: var(--rxt-radius-md);
  background: var(--el-fill-color-light);
}
.as-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 0;
  font-size: 13px;
}
.as-row span:first-child {
  color: var(--el-text-color-secondary);
}
.field-tip {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.approve-warn {
  margin: 4px 0 0;
  padding: 10px 12px;
  border-left: 3px solid var(--rxt-accent);
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.7;
}
</style>
