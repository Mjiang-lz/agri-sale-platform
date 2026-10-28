<template>
  <div class="expert-appointment-page">
    <!-- 页面用途：专家登录后从数据库读取自己的预约并接受或拒绝。 -->
    <section class="expert-hero">
      <div>
        <span class="eyebrow">专家工作台 · 线下预约</span>
        <h2>{{ expert.realName }}，请及时处理农户预约</h2>
        <p>接受后可与农户确认服务时间，暂不能提供服务的预约请填写原因并拒绝</p>
      </div>
      <div class="hero-metric"><strong>{{ pendingCount }}</strong><span>待处理预约</span></div>
    </section>

    <section class="expert-stat-grid">
      <article><span>待处理</span><strong>{{ pendingCount }}</strong><small>需要确认</small></article>
      <article><span>已接受</span><strong>{{ acceptedCount }}</strong><small>等待服务</small></article>
      <article><span>已完成</span><strong>{{ completedCount }}</strong><small>历史服务</small></article>
      <article><span>已拒绝</span><strong>{{ rejectedCount }}</strong><small>无法受理</small></article>
    </section>

    <el-card shadow="never" class="appointment-card">
      <template #header>
        <div class="toolbar">
          <el-radio-group v-model="statusFilter" @change="loadAppointments">
            <el-radio-button value="全部">全部</el-radio-button>
            <el-radio-button value="待处理">待处理</el-radio-button>
            <el-radio-button value="已接受">已接受</el-radio-button>
            <el-radio-button value="已完成">已完成</el-radio-button>
            <el-radio-button value="已拒绝">已拒绝</el-radio-button>
          </el-radio-group>
          <el-button :loading="loading" @click="loadAppointments">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="appointments" :row-key="(row) => row.apptId">
        <el-table-column prop="farmerName" label="农户" width="120">
          <template #default="{ row }">{{ row.farmerName || `农户 #${row.farmerId || '--'}` }}</template>
        </el-table-column>
        <el-table-column prop="apptTopic" label="咨询主题" min-width="240" show-overflow-tooltip />
        <el-table-column label="预约时间" min-width="165"><template #default="{ row }">{{ formatDateTime(row.apptTime) }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusType(row.apptStatus)">{{ row.apptStatus }}</el-tag></template></el-table-column>
        <el-table-column label="备注" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <template v-if="row.apptStatus === '待处理'">
              <el-button link type="success" @click="accept(row)">接受</el-button>
              <el-button link type="danger" @click="openReject(row)">拒绝</el-button>
            </template>
            <span v-else class="processed-text">已处理</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无预约记录" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectVisible" title="拒绝预约" width="min(520px, 92vw)">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
        <el-form-item label="拒绝原因" prop="remark">
          <el-input v-model="rejectForm.remark" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请说明无法接受预约的原因或建议改约时间" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="rejectVisible = false">取消</el-button><el-button type="danger" :loading="submitting" @click="confirmReject">确认拒绝</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { expertOptions, useExperts } from '@/constants/experts'
import { getExpertAppointments, handleExpertAppointment } from '@/api/expert'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const appointments = ref([])
const loading = ref(false)
const submitting = ref(false)
const statusFilter = ref('全部')
const rejectVisible = ref(false)
const rejectTarget = ref(null)
const rejectFormRef = ref()
const rejectForm = reactive({ remark: '' })
const rejectRules = { remark: [{ required: true, message: '请填写拒绝原因', trigger: 'blur' }] }

// ★ expertOptions 现在是响应式 ref（数据驱动），脚本里必须取 .value（同 ExpertAnswerPanel）
const expert = computed(() => expertOptions.value.find((item) => item.userId === Number(userStore.profile?.userId)) || { realName: userStore.profile?.realName || '专家', expertDomain: '农业技术服务' })
const pendingCount = computed(() => appointments.value.filter((item) => item.apptStatus === '待处理').length)
const acceptedCount = computed(() => appointments.value.filter((item) => item.apptStatus === '已接受').length)
const completedCount = computed(() => appointments.value.filter((item) => item.apptStatus === '已完成').length)
const rejectedCount = computed(() => appointments.value.filter((item) => item.apptStatus === '已拒绝').length)

async function loadAppointments() {
  loading.value = true
  try {
    const data = await getExpertAppointments(statusFilter.value)
    appointments.value = Array.isArray(data) ? data : data?.records || []
  } finally {
    loading.value = false
  }
}

async function accept(row) {
  await ElMessageBox.confirm(`确认接受“${row.apptTopic}”这条预约吗？`, '受理预约', { type: 'success' })
  await handleExpertAppointment(row.apptId, { apptStatus: '已接受', remark: '专家已接受预约，请按约定时间联系' })
  ElMessage.success('预约已接受')
  await loadAppointments()
}

function openReject(row) {
  rejectTarget.value = row
  rejectForm.remark = ''
  rejectVisible.value = true
}

async function confirmReject() {
  const valid = await rejectFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await handleExpertAppointment(rejectTarget.value.apptId, {
      apptStatus: '已拒绝',
      remark: rejectForm.remark.trim()
    })
    rejectVisible.value = false
    ElMessage.success('预约已拒绝')
    await loadAppointments()
  } finally {
    submitting.value = false
  }
}

function statusType(status) {
  if (status === '已完成' || status === '已接受') return 'success'
  if (status === '已拒绝') return 'danger'
  return 'warning'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

// 同 ExpertAnswerPanel：要按 userId 反查自己是哪位专家，列表必须来自接口
const { load: loadExperts } = useExperts()
onMounted(() => {
  loadExperts()
  loadAppointments()
})
</script>

<style scoped>
.expert-appointment-page { color: #24332b; }
.expert-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 30px; border-radius: 20px; color: #fff; background: linear-gradient(125deg, #4f3822, #9a6d38); box-shadow: 0 18px 36px rgba(79, 56, 34, .16); }
.eyebrow { color: #f4dfbd; font-size: 13px; }
.expert-hero h2 { margin: 10px 0 8px; font-size: 29px; }
.expert-hero p { margin: 0; max-width: 760px; color: rgba(255,255,255,.76); line-height: 1.7; }
.hero-metric { min-width: 125px; padding: 15px; text-align: center; border: 1px solid rgba(255,255,255,.2); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-metric strong, .hero-metric span { display: block; }
.hero-metric strong { font-size: 30px; }
.hero-metric span { margin-top: 4px; color: rgba(255,255,255,.68); font-size: 12px; }
.expert-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.expert-stat-grid article { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.expert-stat-grid span, .expert-stat-grid small { display: block; color: #75827a; }
.expert-stat-grid strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #8d6424; }
.appointment-card { border-radius: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.processed-text { color: #87938b; font-size: 12px; }
@media (max-width: 1000px) { .expert-stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) { .expert-hero, .toolbar { align-items: stretch; flex-direction: column; } .hero-metric { width: 100%; } .expert-stat-grid { grid-template-columns: 1fr; } }
</style>
