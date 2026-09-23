<template>
  <div class="expert-appointment-page">
    <!--
      页面用途：专家登录后处理预约。
      当前后端只有农户提交预约接口，没有专家预约列表/接受/拒绝接口。
      本组件使用显式“本地演示”数据，操作只保存在当前浏览器，方便前端先行验收。
    -->
    <section class="expert-hero">
      <div>
        <span class="eyebrow">专家工作台 · 线下预约</span>
        <h2>{{ expert.realName }}，请及时处理农户预约</h2>
        <p>接受后可与农户确认服务时间，暂不能提供服务的预约请填写原因并拒绝。</p>
      </div>
      <div class="hero-metric"><strong>{{ pendingCount }}</strong><span>待处理预约</span></div>
    </section>

    <el-alert
      class="demo-alert"
      type="warning"
      :closable="false"
      show-icon
      title="预约管理后端接口尚未提供"
      description="当前列表和处理操作仅用于前端本地演示，数据保存在浏览器 localStorage；后端接口完成后再切换为真实数据源。"
    />

    <section class="expert-stat-grid">
      <article><span>待处理</span><strong>{{ pendingCount }}</strong><small>需要确认</small></article>
      <article><span>已接受</span><strong>{{ acceptedCount }}</strong><small>等待服务</small></article>
      <article><span>已完成</span><strong>{{ completedCount }}</strong><small>历史服务</small></article>
      <article><span>已拒绝</span><strong>{{ rejectedCount }}</strong><small>无法受理</small></article>
    </section>

    <el-card shadow="never" class="appointment-card">
      <template #header>
        <div class="toolbar">
          <el-radio-group v-model="statusFilter">
            <el-radio-button value="全部">全部</el-radio-button>
            <el-radio-button value="待处理">待处理</el-radio-button>
            <el-radio-button value="已接受">已接受</el-radio-button>
            <el-radio-button value="已完成">已完成</el-radio-button>
            <el-radio-button value="已拒绝">已拒绝</el-radio-button>
          </el-radio-group>
          <div>
            <el-button v-if="!allAppointments.length" type="primary" @click="loadDemoData">加载演示预约</el-button>
            <el-button v-if="allAppointments.length" @click="resetDemoData">恢复演示数据</el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredAppointments" :row-key="(row) => row.apptId">
        <el-table-column prop="farmerName" label="农户" width="110" />
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
        <template #empty><el-empty description="暂无预约，可先加载演示预约查看交互" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectVisible" title="拒绝预约" width="min(520px, 92vw)">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
        <el-form-item label="拒绝原因" prop="remark">
          <el-input v-model="rejectForm.remark" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请说明无法接受预约的原因或建议改约时间" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="rejectVisible = false">取消</el-button><el-button type="danger" @click="confirmReject">确认拒绝</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { expertOptions } from '@/constants/experts'
import { useUserStore } from '@/store/user'

const STORAGE_KEY = 'rxt_expert_appointment_workbench'
const userStore = useUserStore()
const allAppointments = ref([])
const statusFilter = ref('全部')
const rejectVisible = ref(false)
const rejectTarget = ref(null)
const rejectFormRef = ref()
const rejectForm = reactive({ remark: '' })
const rejectRules = { remark: [{ required: true, message: '请填写拒绝原因', trigger: 'blur' }] }

const expert = computed(() => expertOptions.find((item) => item.userId === Number(userStore.profile?.userId)) || { realName: userStore.profile?.realName || '专家', expertDomain: '农业技术服务' })
const expertAppointments = computed(() => allAppointments.value.filter((item) => item.expertId === expert.value.expertId))
const filteredAppointments = computed(() => statusFilter.value === '全部' ? expertAppointments.value : expertAppointments.value.filter((item) => item.apptStatus === statusFilter.value))
const pendingCount = computed(() => expertAppointments.value.filter((item) => item.apptStatus === '待处理').length)
const acceptedCount = computed(() => expertAppointments.value.filter((item) => item.apptStatus === '已接受').length)
const completedCount = computed(() => expertAppointments.value.filter((item) => item.apptStatus === '已完成').length)
const rejectedCount = computed(() => expertAppointments.value.filter((item) => item.apptStatus === '已拒绝').length)

const demoData = [
  { apptId: 1, farmerId: 2, farmerName: '李官杏', expertId: 1, apptTime: '2026-09-06 09:00:00', apptTopic: '果园秋施基肥方案现场指导', apptStatus: '已完成', remark: '已完成现场查看。' },
  { apptId: 2, farmerId: 3, farmerName: '王建国', expertId: 1, apptTime: '2026-09-08 14:00:00', apptTopic: '水稻田病虫害防治咨询', apptStatus: '已完成', remark: '已给出绿色防控建议。' },
  { apptId: 3, farmerId: 4, farmerName: '张桂芳', expertId: 2, apptTime: '2026-09-10 10:00:00', apptTopic: '家禽疫病防控指导', apptStatus: '已接受', remark: '请提前整理免疫记录。' },
  { apptId: 4, farmerId: 2, farmerName: '李官杏', expertId: 2, apptTime: '2026-09-15 15:00:00', apptTopic: '鱼塘水质与增氧设备检查', apptStatus: '待处理', remark: '' },
  { apptId: 5, farmerId: 4, farmerName: '张桂芳', expertId: 1, apptTime: '2026-09-14 09:00:00', apptTopic: '果园冬季修剪现场指导', apptStatus: '已拒绝', remark: '当天已有其他行程，建议改约。' }
]

function readLocal() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

function saveLocal() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(allAppointments.value))
}

function loadDemoData() {
  allAppointments.value = demoData.map((item) => ({ ...item }))
  saveLocal()
  ElMessage.success('已加载本地演示预约')
}

async function resetDemoData() {
  await ElMessageBox.confirm('将恢复本地演示数据，不会影响数据库。', '恢复演示数据', { type: 'warning' })
  loadDemoData()
}

async function accept(row) {
  await ElMessageBox.confirm(`确认接受“${row.apptTopic}”这条预约吗？`, '受理预约', { type: 'success' })
  row.apptStatus = '已接受'
  row.remark = '专家已接受预约，请按约定时间联系。'
  saveLocal()
  ElMessage.success('预约已接受')
}

function openReject(row) {
  rejectTarget.value = row
  rejectForm.remark = ''
  rejectVisible.value = true
}

async function confirmReject() {
  const valid = await rejectFormRef.value?.validate().catch(() => false)
  if (!valid) return
  rejectTarget.value.apptStatus = '已拒绝'
  rejectTarget.value.remark = rejectForm.remark.trim()
  saveLocal()
  rejectVisible.value = false
  ElMessage.success('预约已拒绝')
}

function statusType(status) {
  if (status === '已完成' || status === '已接受') return 'success'
  if (status === '已拒绝') return 'danger'
  return 'warning'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

onMounted(() => { allAppointments.value = readLocal() })
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
.demo-alert { margin: 16px 0; }
.expert-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.expert-stat-grid article { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.expert-stat-grid span, .expert-stat-grid small { display: block; color: #75827a; }
.expert-stat-grid strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #8d6424; }
.appointment-card { border-radius: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.processed-text { color: #87938b; font-size: 12px; }
@media (max-width: 1000px) { .expert-stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) { .expert-hero, .toolbar { align-items: stretch; flex-direction: column; } .hero-metric { width: 100%; } .expert-stat-grid { grid-template-columns: 1fr; } }
</style>
