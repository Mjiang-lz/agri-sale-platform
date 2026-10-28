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
      description="只有农户账号可以提交预约，采购方仍可查看专家资料；如需提交预约，请在右上角切换到农户身份"
    />

    <section class="appointment-hero">
      <div>
        <span class="eyebrow">线下专家服务</span>
        <h2>预约专家，获得面对面指导</h2>
        <p>选择领域专家，填写预约时间和咨询主题。预约提交后会进入专家待处理队列</p>
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
      <article
        v-for="expert in filteredExperts"
        :key="expert.expertId"
        class="expert-card"
      >
        <div class="expert-card-head">
          <!-- ★ 必须接 :src：expertAvatar() 优先用库里 avatarUrl（OBS 绝对地址，
               实测 200 可取），库里为空的落到 public/experts/ 的本地图。
               原先这里只有 {{ realName.slice(0,1) }}，所以无论库里有没有头像
               都显示首字方块 —— 这正是「专家页头像要正常显示」的真实病因 -->
          <el-avatar :size="56" :src="expertAvatar(expert)">
            {{ expertInitial(expert.realName) }}
          </el-avatar>
          <div><h3>{{ expert.realName }}</h3><el-tag type="success" effect="plain">{{ expert.expertDomain }}</el-tag></div>
        </div>
        <p>{{ expert.introduction }}</p>
        <!-- ★ v-if：接口兜底名单里 workloadStat 是空串，留着会渲染出一块空黄色条 -->
        <div v-if="expert.workloadStat" class="workload">{{ expert.workloadStat }}</div>
        <div class="expert-card-acts">
          <!-- 介绍页是「想知道这位能不能解决我的问题」的入口（需求 9），
               所以和「预约」并列，而不是把介绍藏在姓名里 -->
          <el-button plain @click="goDetail(expert)">查看介绍</el-button>
          <el-button type="primary" :disabled="!canBookAppointment" @click="openDialog(expert)">预约该专家</el-button>
        </div>
      </article>
      <el-empty v-if="!filteredExperts.length" description="没有找到符合条件的专家" />
    </section>

    <el-card shadow="never" class="recent-card">
      <template #header>
        <div class="recent-head">
          <div>
            <strong>我的预约</strong>
            <span>含专家处理意见的完整预约记录</span>
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
        <el-avatar :size="46" :src="expertAvatar(selectedExpert) || undefined">{{ expertInitial(selectedExpert.realName) }}</el-avatar>
        <div><strong>{{ selectedExpert.realName }} · {{ selectedExpert.expertDomain }}</strong><span v-if="selectedExpert.workloadStat">{{ selectedExpert.workloadStat }}</span></div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import ExpertAppointmentPanel from '@/components/expert/ExpertAppointmentPanel.vue'
import { getMyAppointments, submitAppointment } from '@/api/expert'
import { expertOptions, useExperts } from '@/constants/experts'
import { expertAvatar, expertInitial } from '@/utils/imagery'
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
const router = useRouter()
// 专家列表来自 GET /expert/experts（原先是 constants/experts.js 里写死的两位）
const { load: loadExperts } = useExperts()
// 管理员与农户同口径（与 MainLayout 的 isFarmerLike 一致）：
// 农户是平台主体、预约是通用能力，管理员也应当能看到预约入口。
// ⚠️ 这只管「能否看到/预约」，真正写入仍由后端 requireRole 拦截。
const canBookAppointment = computed(() => ['农户', '管理员'].includes(userStore.profile?.roleName))
const isExpert = computed(() => userStore.profile?.roleName === '专家')
// ★ expertOptions 现在是响应式 ref（数据驱动）。模板里 ref 会自动解包，
//   但 JS 里拿到的是 ref 对象 —— 直接 experts.filter(...) 会抛
//   「experts.filter is not a function」，整页白屏且控制台只有一条
//   Vue warn，没有别的线索。脚本里一律取 .value。
const experts = expertOptions
const appointments = ref([])
const loadingAppointments = ref(false)
/** 预约列表的加载失败原因。非空时表格位置展示错误态，避免「加载失败」看起来像「一条都没有」 */
const appointmentError = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const selectedExpert = ref(null)
/**
 * ★ 2026-10-12：支持从商品列表页的空态引导带关键词跳进来
 * （商品搜不到 → 「在专家里搜「xxx」」，见 trade/ProductList.vue）。
 * 地址栏参数用户能手改，`?keyword=a&keyword=b` 会被解析成数组，
 * 直接塞进 el-input 会渲染成 "a,b"，所以只认字符串。
 */
const pickQuery = (v) => (typeof v === 'string' ? v : '')
const route = useRoute()
const keyword = ref(pickQuery(route.query.keyword))
const domain = ref('全部')
const formRef = ref()
/**
 * 领域筛选（需求 6）。原先写死 ['全部','种植与植保','畜牧与水产'] ——
 * 新增专家后他的领域不在这个列表里，用户就筛不出来（虽然卡片还在，
 * 但「按领域找专家」这个最自然的用法直接失效了）。改成从专家列表派生。
 */
const domains = computed(() => ['全部', ...new Set(experts.value.map((e) => e.expertDomain).filter(Boolean))])
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

const filteredExperts = computed(() => experts.value.filter((item) => {
  const domainMatch = domain.value === '全部' || item.expertDomain === domain.value
  const key = keyword.value.trim()
  const keywordMatch = !key || `${item.realName}${item.expertDomain}${item.introduction}`.includes(key)
  return domainMatch && keywordMatch
}))

// 本页可能已在另一个 tab / 上一次跳转中打开过，此时再带新关键词跳进来，
// 组件不会重新挂载（onMounted 不再跑），所以必须监听一次路由参数。
watch(
  () => route.query.keyword,
  (v) => { keyword.value = pickQuery(v) }
)

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
  if (!canBookAppointment.value) {
    // 只有农户/管理员有「我的预约」可查。
    // ★ 专家**绝不能**走这里：GET /expert/appointment/my 在 ExpertController 上写死
    //   requireRole(FARMER)，专家一调必然吃 403「没有该操作的权限」，在页面右上角
    //   弹一条红色提示 —— 而专家进本页时渲染的是 ExpertAppointmentPanel
    //   （走 /expert/appointment/expert），下面这张表根本不显示，请求纯属自找报错。
    //   原判据是 `!canBookAppointment && !isExpert`，把专家放了进来，正是这条误报的来源。
    appointments.value = []
    return
  }
  loadingAppointments.value = true
  try {
    const data = await getMyAppointments()
    appointments.value = Array.isArray(data) ? data : []
  } catch (err) {
    appointments.value = []
    appointmentError.value = err?.message || '数据加载失败，请稍后刷新重试'
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

/** 跳到该专家的介绍页（需求 9） */
function goDetail(expert) { router.push(`/expert/${expert.expertId}`) }

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
onMounted(() => {
  // 专家列表走接口（原先是写死的两位），不拉就只有兜底名单
  loadExperts()
  loadAppointments()
})
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
/* 「查看介绍」与「预约」并列：介绍页是「这位能不能解决我的问题」的判断依据 */
.expert-card-acts { display: flex; gap: 10px; }
.expert-card-acts .el-button { flex: 1; margin-left: 0; }
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
