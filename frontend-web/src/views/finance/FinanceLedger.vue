<template>
  <div class="finance-ledger-page">
    <!--
      页面用途：管理员在业务端只读查看全平台融资申请台账。

      为什么需要这一页（而不是让管理员直接用「我的融资」）：
      /finance/apply/my 走 requireRole(FARMER) 且只返回本人提交，管理员调它会 403；
      就算放行也看不到别人的数据。台账走 /admin/finance/applies，
      后端是 requireAdmin() 放行的只读查询，数据范围是全平台。

      与管理后台融资管理页的关系：同一后端接口、不同前端壳。
      这里是业务端（顶栏导航）内的一个只读入口，方便管理员不切后台也能核对台账。
    -->
    <div class="page-head">
      <div>
        <h2>融资台账</h2>
        <p>只读查看全平台融资申请与银行审批结论。审批动作由银行用户完成，本页不提供审批入口。</p>
      </div>
      <div class="head-actions">
        <el-tag type="info" effect="plain">管理员只读</el-tag>
        <el-button :loading="loading" @click="loadLedger">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="数据说明"
      description="本页展示全部融资申请。农户提交入口见「融资申请」（只读），审批入口仅银行用户可见。"
      class="ledger-hint"
    />

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <el-select v-model="query.applyStatus" clearable placeholder="全部状态" style="width: 170px" @change="handleSearch">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索申请编号或农户姓名"
            style="width: 250px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" row-key="applyId">
        <el-table-column prop="applyNo" label="申请编号" min-width="160" />
        <el-table-column label="农户" min-width="150">
          <template #default="{ row }">
            <span>{{ row.farmerName || '--' }}</span>
            <el-tag v-if="row.farmerMainProduct" size="small" effect="plain" class="product-tag">
              {{ row.farmerMainProduct }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请额度" min-width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.applyAmount) }} 元</template>
        </el-table-column>
        <el-table-column label="期限" width="90">
          <template #default="{ row }">{{ row.applyTerm }} 个月</template>
        </el-table-column>
        <el-table-column label="贷款产品" min-width="150">
          <template #default="{ row }">{{ row.loanProductName || `产品 #${row.loanProductId || '--'}` }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="125">
          <template #default="{ row }">
            <el-tag :type="statusType(row.applyStatus)">{{ row.applyStatus || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="银行结论" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.approvalResult" :type="row.approvalResult === '通过' ? 'success' : 'danger'" effect="plain">
              {{ row.approvalResult }}
            </el-tag>
            <span v-else class="muted">未审批</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无融资申请记录" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadLedger"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 详情：管理员看得到农户信息与银行结论，这正是治理视角需要的东西。
         与农户侧的「进度时间轴」不同，这里不做时间轴，只如实列字段 ——
         审批链路的时间线由银行侧页面负责。 -->
    <el-drawer v-model="detailVisible" title="融资申请详情" size="min(660px, 96%)">
      <template v-if="currentRow">
        <div class="detail-header">
          <div>
            <span>{{ currentRow.applyNo }}</span>
            <h3>{{ currentRow.loanProductName || '融资申请' }}</h3>
          </div>
          <el-tag :type="statusType(currentRow.applyStatus)">{{ currentRow.applyStatus || '--' }}</el-tag>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="农户">{{ currentRow.farmerName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentRow.farmerPhone || '--' }}</el-descriptions-item>
          <el-descriptions-item label="主营品类">{{ currentRow.farmerMainProduct || '--' }}</el-descriptions-item>
          <el-descriptions-item label="贷款产品">{{ currentRow.loanProductName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="申请额度">{{ formatMoney(currentRow.applyAmount) }} 元</el-descriptions-item>
          <el-descriptions-item label="申请期限">{{ currentRow.applyTerm }} 个月</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDateTime(currentRow.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="推送银行">{{ formatDateTime(currentRow.pushTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.applyPurpose" label="资金用途" :span="2">
            {{ currentRow.applyPurpose }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">银行审批结论</div>
        <el-descriptions v-if="currentRow.approvalResult" :column="2" border>
          <el-descriptions-item label="审批结果">
            <el-tag :type="currentRow.approvalResult === '通过' ? 'success' : 'danger'" effect="plain">
              {{ currentRow.approvalResult }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="核准放款">
            {{ currentRow.loanAmount != null ? formatMoney(currentRow.loanAmount) + ' 元' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ formatDateTime(currentRow.approvalTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批意见">{{ currentRow.approvalOpinion || '--' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="尚未产生审批结论" :image-size="72" />

        <div class="drawer-note">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="本页为只读台账"
            description="审批动作需由银行用户在「融资服务 › 融资审批」完成；管理员在此不能出结论。"
          />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getFinanceLedger } from '@/api/finance'
import { formatDateTime } from '@/utils/text'

/**
 * 管理员只读融资台账。
 *
 * ⚠️ 状态筛选项必须与后端 t_finance_apply.apply_status 的取值一致。
 * 后端 pageApplies 是按 applyStatus **精确匹配**过滤的，
 * 前端传一个库里不存在的状态词不会报错，只会静默返回空列表 ——
 * 那种「筛选器有选项但永远查不出东西」的问题不看数据是发现不了的。
 */
const STATUS_OPTIONS = ['待匹配', '已组建小组', '已推送银行', '审批通过', '审批未通过', '已放款']

const loading = ref(false)
const records = ref([])
const total = ref(0)
const detailVisible = ref(false)
const currentRow = ref(null)

const query = reactive({
  applyStatus: '',
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

async function loadLedger() {
  loading.value = true
  try {
    const data = await getFinanceLedger({
      applyStatus: query.applyStatus || undefined,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    // ⚠️ 后端返回 MyBatis-Plus 分页对象，列表在 records 上。
    //   误写成 data.list 会让表格永远空白，且不报任何错。
    records.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadLedger()
}

function handleReset() {
  query.applyStatus = ''
  query.keyword = ''
  query.pageNum = 1
  loadLedger()
}

function openDetail(row) {
  currentRow.value = row
  detailVisible.value = true
}

function formatMoney(value) {
  if (value == null || value === '') return '--'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

/** 状态标签配色。与 MyApplies.vue 的 statusType 同一口径。 */
function statusType(status) {
  const map = {
    待匹配: 'info',
    已组建小组: 'warning',
    已推送银行: 'warning',
    审批通过: 'success',
    审批未通过: 'danger',
    已放款: 'success'
  }
  return map[status] || 'info'
}

onMounted(loadLedger)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
.ledger-hint {
  margin-bottom: 14px;
}
.product-tag {
  margin-left: 6px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.detail-header span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-header h3 {
  margin: 4px 0 0;
  font-size: 16px;
}
.section-title {
  margin: 22px 0 12px;
  font-weight: 600;
}
.drawer-note {
  margin-top: 22px;
}
</style>