<template>
  <ExpertAppointmentPanel v-if="isExpert" />

  <div v-else class="appointment-page">
    <!-- 页面用途：农户筛选专家、提交线下预约，并查看服务端的完整预约记录与专家处理意见。 -->
    <el-alert
      v-if="!canBookAppointment"
      class="permission-alert"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号不能提交专家预约"
      description="只有农户账号可以提交预约；采购方仍可查看专家资料。若需要切换演示身份，请使用右上角身份切换。"
    />

    <section class="appointment-hero">
      <div>
        <span class="eyebrow">线下专家服务</span>
        <h2>预约专家，获得面对面指导</h2>
        <p>选择领域专家，填写预约时间和咨询主题。预约提交后会进入专家待处理队列。</p>
      </div>
      <div class="hero-badge"><strong>{{ experts.length }}</strong><span>位专家可预约</span></div>
    </section>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" clearable placeholder="搜索专家姓名、领域或服务方向" />
        <el-select v-model="domain" style="width: 190px">
          <el-option v-for="item in domains" :key="item" :label="item" :value="item" />
        </el-select>
      </div>
    </el-card>

    <section class="expert-grid">
      <article v-for="expert in filteredExperts" :key="expert.expertId" class="expert-card">
        <div class="expert-card-head">
          <el-avatar :size="56">{{ expert.realName.slice(0, 1) }}</el-avatar>
          <div><h3>{{ expert.realName }}</h3><el-tag type="success" effect="plain">{{ expert.expertDomain }}</el-tag></div>
        </div>
        <p>{{ expert.introduction }}</p>
        <div class="workload">{{ expert.workloadStat }}</div>
        <el-button type="primary" style="width: 100%" :disabled="!canBookAppointment" @click="openDialog(expert)">预约该专家</el-button>
      </article>
      <el-empty v-if="!filteredExperts.length" description="没有找到符合条件的专家" />
    </section>

    <el-card shadow="never" class="recent-card">
      <template #header>
        <div class="recent-head">
          <div>
            <strong>我的预约</strong>
            <span>来自服务端的完整预约记录，含专家的处理意见；刷新不会丢失。</span>
          </div>
          <el-button :loading="loadingAppointments" @click="loadAppointments">刷新</el-button>
        </div>
      </template>

      <el-alert
        v-if="appointmentError"
        class="appointment-error"
        type="error"
        :closable="false"
        show-icon
        title="预约记录加载失败"
        :description="appointmentError"
      />

      <el-table v-else v-loading="loadingAppointments" :data="appointments" row-key="apptId">
        <el-table-column label="专家" min-width="150">
          <template #default="{ row }">
            <div class="expert-cell">
              <strong>{{ row.expertName || `专家 #${row.expertId}` }}</strong>
              <small v-if="row.expertDomain">{{ row.expertDomain }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="apptTopic" label="咨询主题" min-width="200" show-overflow-tooltip />
        <el-table-column label="预约时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.apptTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.apptStatus)">{{ row.apptStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理意见" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="还没有预约记录" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="提交专家预约" width="min(620px, 94vw)" destroy-on-close>
      <div v-if="selectedExpert" class="selected-expert">
        <el-avatar :size="46">{{ selectedExpert.realName.slice(0, 1) }}</el-avatar>
        <div><strong>{{ selectedExpert.realName }} · {{ selectedExpert.expertDomain }}</strong><span>{{ selectedExpert.workloadStat }}</span></div>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="预约专家" prop="expertId">
          <el-select v-model="form.expertId" style="width: 100%">
            <el-option v-for="expert in experts" :key="expert.expertId" :label="`${expert.realName} · ${expert.expertDomain}`" :value="expert.expertId" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间" prop="apptTime">
          <el-date-picker v-model="form.apptTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="请选择预约时间" :disabled-date="disabledDate" style="width: 100%" />
        </el-form-item>
        <el-form-item label="咨询主题" prop="apptTopic">
          <el-input v-model="form.apptTopic" maxlength="200" show-word-limit placeholder="例如：果园秋施肥方案现场指导" />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="可填写作物规模、当前问题或希望准备的材料" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确认预约</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ExpertAppointmentPanel from '@/components/expert/ExpertAppointmentPanel.vue'
import { getMyAppointments, submitAppointment } from '@/api/expert'
import { expertOptions } from '@/constants/experts'
import { useUserStore } from '@/store/user'

/**
 * 页面用途：专家预约。
 * 接口文件：frontend-web/src/api/expert.js
 * - submitAppointment：POST /api/expert/appointment/submit，真实写入 t_appointment。
 * - getMyAppointments：GET /api/expert/appointment/my，读取当前农户的完整预约记录。
 *
 * 说明：此前「我的预约」没有查询接口，页面把记录存在 localStorage 里，
 * 换浏览器/清缓存就没了，也拿不到专家的处理意见。现已改为读取服务端数据。
 */
const userStore = useUserStore()
const canBookAppointment = computed(() => userStore.profile?.roleName === '农户')
const isExpert = computed(() => userStore.profile?.roleName === '专家')
const experts = expertOptions
const appointments = ref([])
const loadingAppointments = ref(false)
/** 预约列表的加载失败原因。非空时表格位置展示错误态，避免「加载失败」看起来像「一条都没有」 */
const appointmentError = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const selectedExpert = ref(null)
const keyword = ref('')
const domain = ref('全部')
const formRef = ref()
const domains = ['全部', '种植与植保', '畜牧与水产']
const form = reactive({ expertId: null, apptTime: '', apptTopic: '', remark: '' })

function validateFutureTime(rule, value, callback) {
  if (!value || new Date(value).getTime() > Date.now()) {
    callback()
    return
  }
  callback(new Error('预约时间必须晚于当前时间'))
}

const rules = {
  expertId: [{ required: true, message: '请选择专家', trigger: 'change' }],
  apptTime: [
    { required: true, message: '请选择预约时间', trigger: 'change' },
    { validator: validateFutureTime, trigger: 'change' }
  ],
  apptTopic: [{ required: true, message: '请填写咨询主题', trigger: 'blur' }]
}

const filteredExperts = computed(() => experts.filter((item) => {
  const domainMatch = domain.value === '全部' || item.expertDomain === domain.value
  const key = keyword.value.trim()
  const keywordMatch = !key || `${item.realName}${item.expertDomain}${item.introduction}`.includes(key)
  return domainMatch && keywordMatch
}))

function disabledDate(date) { return date.getTime() < new Date().setHours(0, 0, 0, 0) }

/**
 * 读取我的预约。
 *
 * 失败时把原因留在页面上，而不是让表格空着 —— 「加载失败」与「一条都没有」
 * 在界面上必须区分开，否则用户会以为预约记录丢了。
 * toast 已由 request.js 统一弹过，这里只渲染行内错误态。
 */
async function loadAppointments() {
  appointmentError.value = ''
  if (!canBookAppointment.value && !isExpert.value) {
    // 非农户/专家身份没有预约记录可查，不必请求（后端也会 403）
    appointments.value = []
    return
  }
  loadingAppointments.value = true
  try {
    const data = await getMyAppointments()
    appointments.value = Array.isArray(data) ? data : []
  } catch (err) {
    appointments.value = []
    appointmentError.value = err?.message || '无法连接后端服务，请确认后端已启动后重试。'
  } finally {
    loadingAppointments.value = false
  }
}

/** 状态对应的标签颜色，让「已拒绝」和「已完成」一眼能分开 */
function statusTagType(status) {
  return {
    待处理: 'warning',
    已接受: 'primary',
    已完成: 'success',
    已拒绝: 'danger',
    已取消: 'info'
  }[status] || 'info'
}

function openDialog(expert) { selectedExpert.value = expert; form.expertId = expert.expertId; form.apptTime = ''; form.apptTopic = ''; form.remark = ''; dialogVisible.value = true }

async function handleSubmit() {
  if (!canBookAppointment.value) {
    ElMessage.warning('只有农户账号可以提交专家预约')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await submitAppointment({ expertId: form.expertId, apptTime: form.apptTime, apptTopic: form.apptTopic, remark: form.remark })
    dialogVisible.value = false
    ElMessage.success('预约已提交，专家处理后可在线下联系确认')
    // 重新拉取列表，让新记录带上服务端生成的编号与状态，
    // 而不是在前端拼一条「看起来像」的记录塞进表格
    await loadAppointments()
  } catch (err) {
    console.error('提交专家预约失败：', err)
  } finally { submitting.value = false }
}

function formatDateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
onMounted(loadAppointments)
</script>

<style scoped>
.appointment-page { color: var(--el-text-color-primary); }
.permission-alert { margin-bottom: 16px; }
.appointment-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 30px; color: #fff; border-radius: 20px; background: linear-gradient(125deg, #4f3822, #9a6d38); box-shadow: 0 18px 36px rgba(79, 56, 34, .16); }
.eyebrow { color: #f4dfbd; font-size: 13px; }
.appointment-hero h2 { margin: 10px 0 8px; font-size: 30px; }
.appointment-hero p { margin: 0; max-width: 720px; color: rgba(255,255,255,.76); line-height: 1.7; }
.hero-badge { min-width: 120px; padding: 16px; text-align: center; border: 1px solid rgba(255,255,255,.2); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-badge strong, .hero-badge span { display: block; }
.hero-badge strong { font-size: 28px; }
.hero-badge span { margin-top: 4px; color: rgba(255,255,255,.68); font-size: 12px; }
.filter-card { margin: 18px 0; border-radius: 14px; }
.filter-row { display: grid; grid-template-columns: minmax(0, 1fr) 190px; gap: 12px; }
.expert-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.expert-card { padding: 20px; border: 1px solid var(--el-border-color-light); border-radius: 16px; background: #fff; transition: .2s; }
.expert-card:hover { transform: translateY(-4px); box-shadow: 0 16px 34px rgba(31, 45, 39, .1); }
.expert-card-head { display: flex; align-items: center; gap: 13px; }
.expert-card-head h3 { margin: 0 0 7px; }
.expert-card > p { min-height: 74px; color: var(--el-text-color-secondary); line-height: 1.7; }
.workload { margin: 14px 0; padding: 11px; border-radius: 10px; color: #8d6424; background: #fff8e8; font-size: 12px; }
.recent-card { border-radius: 16px; }
.recent-head { display: flex; justify-content: space-between; align-items: center; gap: 14px; }
.recent-head strong, .recent-head span { display: block; }
.recent-head span { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.selected-expert { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 13px; border-radius: 12px; background: var(--el-color-primary-light-9); }
.selected-expert strong, .selected-expert span { display: block; }
.selected-expert span { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.expert-cell strong, .expert-cell small { display: block; }
.expert-cell small { margin-top: 3px; color: var(--el-text-color-secondary); font-size: 12px; }
.appointment-error { margin-bottom: 4px; }
@media (max-width: 760px) { .appointment-hero { align-items: flex-start; flex-direction: column; } .hero-badge { width: 100%; } .filter-row, .expert-grid { grid-template-columns: 1fr; } .recent-head { align-items: flex-start; flex-direction: column; } }
</style>
