<template>
  <div class="appointment-page">
    <!-- 页面用途：农户筛选专家、提交线下预约并查看当前浏览器中的最近提交记录。 -->
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
        <el-button type="primary" style="width: 100%" @click="openDialog(expert)">预约该专家</el-button>
      </article>
      <el-empty v-if="!filteredExperts.length" description="没有找到符合条件的专家" />
    </section>

    <el-card shadow="never" class="recent-card">
      <template #header>
        <div class="recent-head">
          <div><strong>最近提交</strong><span>预约提交接口已写入后端，这里保留当前浏览器的最近记录</span></div>
          <el-button v-if="appointments.length" text type="danger" @click="clearAppointments">清空本地记录</el-button>
        </div>
      </template>
      <el-table :data="appointments" row-key="apptId">
        <el-table-column prop="expertName" label="专家" width="110" />
        <el-table-column prop="apptTopic" label="咨询主题" min-width="250" show-overflow-tooltip />
        <el-table-column label="预约时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.apptTime) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag type="warning">{{ row.apptStatus }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitAppointment } from '@/api/expert'
import { expertOptions } from '@/constants/experts'

/**
 * 页面用途：专家预约。
 * 接口文件：frontend-web/src/api/expert.js
 * - submitAppointment：POST /api/expert/appointment/submit，真实写入 t_appointment。
 * - 后端暂未提供“我的预约”查询接口，最近提交记录保存在 localStorage。
 */
const STORAGE_KEY = 'rxt_expert_appointments'
const experts = expertOptions
const appointments = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const selectedExpert = ref(null)
const keyword = ref('')
const domain = ref('全部')
const formRef = ref()
const domains = ['全部', '种植与植保', '畜牧与水产']
const form = reactive({ expertId: null, apptTime: '', apptTopic: '', remark: '' })

const rules = {
  expertId: [{ required: true, message: '请选择专家', trigger: 'change' }],
  apptTime: [{ required: true, message: '请选择预约时间', trigger: 'change' }],
  apptTopic: [{ required: true, message: '请填写咨询主题', trigger: 'blur' }]
}

const filteredExperts = computed(() => experts.filter((item) => {
  const domainMatch = domain.value === '全部' || item.expertDomain === domain.value
  const key = keyword.value.trim()
  const keywordMatch = !key || `${item.realName}${item.expertDomain}${item.introduction}`.includes(key)
  return domainMatch && keywordMatch
}))

function disabledDate(date) { return date.getTime() < new Date().setHours(0, 0, 0, 0) }
function readLocal() { try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]') } catch { return [] } }
function saveLocal() { localStorage.setItem(STORAGE_KEY, JSON.stringify(appointments.value)) }
function openDialog(expert) { selectedExpert.value = expert; form.expertId = expert.expertId; form.apptTime = ''; form.apptTopic = ''; form.remark = ''; dialogVisible.value = true }

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const apptId = await submitAppointment({ expertId: form.expertId, apptTime: form.apptTime, apptTopic: form.apptTopic, remark: form.remark })
    const expert = experts.find((item) => item.expertId === form.expertId)
    appointments.value.unshift({ apptId, expertName: expert?.realName || '专家', apptTopic: form.apptTopic, apptTime: form.apptTime.replace('T', ' '), apptStatus: '待处理', remark: form.remark, createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') })
    saveLocal()
    dialogVisible.value = false
    ElMessage.success('预约已提交，专家处理后可在线下联系确认')
  } finally { submitting.value = false }
}

async function clearAppointments() {
  await ElMessageBox.confirm('仅清空当前浏览器中的最近提交显示，不会删除数据库预约记录。', '确认清空', { type: 'warning' })
  appointments.value = []
  saveLocal()
}

function formatDateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
onMounted(() => { appointments.value = readLocal() })
</script>

<style scoped>
.appointment-page { color: var(--el-text-color-primary); }
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
@media (max-width: 760px) { .appointment-hero { align-items: flex-start; flex-direction: column; } .hero-badge { width: 100%; } .filter-row, .expert-grid { grid-template-columns: 1fr; } .recent-head { align-items: flex-start; flex-direction: column; } }
</style>
