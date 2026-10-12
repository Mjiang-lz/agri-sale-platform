<template>
  <div class="expert-manage-page">
    <!--
      页面用途：平台管理员维护专家档案（SRS 管理员职责「专家资源管理」）。
      默认显示全部档案（含待审核），可切换审核状态、按擅长领域筛选，编辑领域/简介/审核状态。

      与「内容审核」三个页面是不同职责：那里审的是商品/知识/评论，走 t_audit 留痕；
      这里管的是专家主体资料。两者在导航上刻意分开，不要合并。
    -->
    <section class="manage-hero">
      <div>
        <span class="eyebrow">M1 · 专家资源治理</span>
        <h2>专家资源管理</h2>
        <p>维护入驻专家的擅长领域与服务简介，并处理入驻审核状态。领域与简介会直接展示在业务端的专家页上。</p>
      </div>
      <div class="hero-status">
        <span>当前待审</span>
        <strong>{{ pendingCount }}</strong>
        <small>位专家</small>
      </div>
    </section>

    <section class="stat-grid">
      <article class="stat-card">
        <span>专家总数</span>
        <strong>{{ allExperts.length }}</strong>
        <small>含全部审核状态</small>
      </article>
      <article class="stat-card">
        <span>待审核</span>
        <strong>{{ countByStatus('待审核') }}</strong>
        <small>尚未在业务端公开</small>
      </article>
      <article class="stat-card">
        <span>已通过</span>
        <strong>{{ countByStatus('已通过') }}</strong>
        <small>业务端专家页可见</small>
      </article>
      <article class="stat-card">
        <span>未通过</span>
        <strong>{{ countByStatus('未通过') }}</strong>
        <small>需联系专家补充资料</small>
      </article>
    </section>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <div class="toolbar-filters">
            <el-radio-group v-model="filterStatus" @change="loadList">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="待审核">待审核</el-radio-button>
              <el-radio-button value="已通过">已通过</el-radio-button>
              <el-radio-button value="未通过">未通过</el-radio-button>
            </el-radio-group>
            <!--
              选项取自「全量」列表（allExperts）而不是当前列表 ——
              领域是精确匹配筛选，若选项跟着筛选结果收缩，选了某项之后就再也切不回去了。
            -->
            <el-select
              v-model="filterDomain"
              placeholder="全部领域"
              clearable
              style="width: 180px"
              @change="loadList"
            >
              <el-option v-for="d in domainOptions" :key="d" :label="d" :value="d" />
            </el-select>
          </div>
          <div class="toolbar-actions">
            <span class="record-count">共 {{ total }} 条</span>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" :row-key="(row) => row.expertId">
        <el-table-column label="专家" min-width="200">
          <template #default="{ row }">
            <div class="expert-cell">
              <el-avatar :size="36" :src="row.avatarUrl || undefined">
                {{ (row.realName || '?').slice(0, 1) }}
              </el-avatar>
              <div class="expert-cell-text">
                <strong>{{ row.realName || `用户 #${row.userId || '--'}` }}</strong>
                <small>专家 #{{ row.expertId }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="expertDomain" label="擅长领域" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.expertDomain" type="success" effect="plain">{{ row.expertDomain }}</el-tag>
            <span v-else class="muted">未填写</span>
          </template>
        </el-table-column>
        <el-table-column prop="introduction" label="简介与服务范围" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.introduction || '--' }}</template>
        </el-table-column>
        <el-table-column label="工作量" width="120">
          <template #default="{ row }">
            <span class="muted">{{ row.workloadStat || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.auditStatus)">{{ row.auditStatus || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建档时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有专家档案" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑专家档案" width="min(620px, 92vw)">
      <template v-if="editForm">
        <el-descriptions :column="1" border class="edit-head">
          <el-descriptions-item label="专家">
            {{ editForm.realName || `用户 #${editForm.userId || '--'}` }}（专家 #{{ editForm.expertId }}）
          </el-descriptions-item>
          <el-descriptions-item label="服务工作量">
            <span class="muted">{{ editForm.workloadStat || '--' }}</span>
            <span class="readonly-hint">系统统计，不在此处编辑</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-form :model="form" label-width="96px" class="edit-form">
          <el-form-item label="擅长领域">
            <el-input v-model="form.expertDomain" maxlength="50" show-word-limit placeholder="例如：柑橘种植" />
          </el-form-item>
          <el-form-item label="简介">
            <el-input
              v-model="form.introduction"
              type="textarea"
              :rows="5"
              placeholder="专家介绍与服务范围，会展示在业务端专家页"
            />
          </el-form-item>
          <el-form-item label="审核状态">
            <el-select v-model="form.auditStatus" style="width: 100%">
              <el-option v-for="s in AUDIT_STATUS" :key="s" :label="s" :value="s" />
            </el-select>
            <p class="form-hint">
              此处改动属资料维护，<b>不写审核留痕</b>，不计入「内容审核」页的审核工作量。
              当前没有对专家主体留痕的审核接口，这是唯一能改该状态的入口。
            </p>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminExpertList, updateAdminExpert } from '@/api/expert'

/** 与后端 AdminExpertUpdateDTO 的白名单正则逐字对齐，不要另取名目 */
const AUDIT_STATUS = ['待审核', '已通过', '未通过']

const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const rows = ref([])
const total = ref(0)
/** 不带筛选条件的全量快照：只用来算统计卡和领域下拉，不参与表格渲染 */
const allExperts = ref([])

const filterStatus = ref('')
const filterDomain = ref('')

const editForm = ref(null)
const form = reactive({ expertDomain: '', introduction: '', auditStatus: '' })

const pendingCount = computed(() => countByStatus('待审核'))

const domainOptions = computed(() => {
  const set = new Set()
  for (const item of allExperts.value) {
    if (item.expertDomain) set.add(item.expertDomain)
  }
  return Array.from(set).sort()
})

function countByStatus(status) {
  return allExperts.value.filter((item) => item.auditStatus === status).length
}

function statusType(status) {
  if (status === '已通过') return 'success'
  if (status === '未通过') return 'danger'
  if (status === '待审核') return 'warning'
  return 'info'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

/**
 * 拉取列表。
 *
 * 列表接口**不返回分页包层**，`data` 直接就是数组 —— 这里不写 `data.records`。
 * 全量快照只在「无任何筛选」时刷新：筛选后列表会变短，拿它当统计口径
 * 会让统计卡跟着筛选条件跳动，看着像数据丢了。
 */
async function loadList() {
  loading.value = true
  try {
    const data = await getAdminExpertList({
      auditStatus: filterStatus.value,
      domain: filterDomain.value
    })
    rows.value = Array.isArray(data) ? data : []
    total.value = rows.value.length

    if (!filterStatus.value && !filterDomain.value) {
      allExperts.value = rows.value
    }
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  editForm.value = row
  form.expertDomain = row.expertDomain || ''
  form.introduction = row.introduction || ''
  form.auditStatus = row.auditStatus || AUDIT_STATUS[0]
  editVisible.value = true
}

/**
 * 保存。
 *
 * 三个字段的「空值语义」不一致，是后端定的口径，别想当然：
 *   · expertDomain 空串 = 不改（后端判 hasText）
 *   · introduction 空串 = 清空（后端判 != null）
 *   · auditStatus  空串 = 不改
 * 所以这里**原样提交三个字段**，不做「跳过没改的」优化 —— 那会让
 * 「清空简介」这个合法操作没法表达（跳过 = 内容留着，用户以为删掉了）。
 */
async function save() {
  saving.value = true
  try {
    await updateAdminExpert(editForm.value.expertId, {
      expertDomain: form.expertDomain,
      introduction: form.introduction,
      auditStatus: form.auditStatus
    })
    editVisible.value = false
    ElMessage.success('专家档案已更新')
    await loadList()
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.expert-manage-page { color: #24332b; }
.manage-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #173f2a, #2b8154); box-shadow: 0 16px 34px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }
.manage-hero h2 { margin: 9px 0 7px; font-size: 26px; }
.manage-hero p { margin: 0; max-width: 760px; color: rgba(255,255,255,.72); line-height: 1.7; }
.hero-status { min-width: 130px; padding: 14px 20px; text-align: center; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-status span, .hero-status small { display: block; color: rgba(255,255,255,.66); }
.hero-status strong { display: block; margin: 4px 0; font-size: 28px; }

.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.stat-card { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.stat-card span, .stat-card small { display: block; color: #75827a; }
.stat-card strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #1d5637; }
.stat-card small { font-size: 11px; }

.table-card { border-radius: 15px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.toolbar-filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; }
.record-count { color: #75827a; font-size: 12px; }

.expert-cell { display: flex; align-items: center; gap: 10px; }
.expert-cell-text { display: flex; flex-direction: column; line-height: 1.35; }
.expert-cell-text small { color: #839087; font-size: 11px; }
.muted { color: #839087; }

.edit-head { margin-bottom: 18px; }
.readonly-hint { margin-left: 8px; color: #839087; font-size: 12px; }
.form-hint { margin: 6px 0 0; color: #839087; font-size: 12px; line-height: 1.7; }

@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) {
  .manage-hero, .toolbar { align-items: stretch; flex-direction: column; }
  .hero-status { width: 100%; }
  .stat-grid { grid-template-columns: 1fr; }
}
</style>
