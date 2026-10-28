<template>
  <ExpertAnswerPanel v-if="isExpert" />

  <div v-else class="qa-page">
    <!-- 页面用途：农户查看自己的历史提问，选择平台专家并提交新的农业问题。 -->
    <section class="qa-hero">
      <div>
        <span class="eyebrow">专家在线答疑</span>
        <h2>有问题，交给懂农业的人</h2>
        <p>描述作物或养殖情况，选择擅长领域的专家，也可以让平台自动分派</p>
      </div>
      <div class="hero-metrics">
        <div><strong>{{ questions.length }}</strong><span>我的提问</span></div>
        <div><strong>{{ repliedCount }}</strong><span>已回复</span></div>
        <div><strong>{{ pendingCount }}</strong><span>待回复</span></div>
      </div>
    </section>

    <section class="expert-strip">
      <!-- ★ 点整张卡 = 进这位专家的介绍页（用户 2026-10-13 要求「点各个专家卡片
           对应各个专家的介绍页」）。原先点一下是「选中该专家」，但同一件事下方
           「指定专家」下拉本来就能做，所以这里让位给介绍页，功能不丢。
           ★ .active 高亮仍跟着 form.expertId 走：从下拉选人时卡片会亮起来，
             这条视觉线索保留，只是不再由点击产生。 -->
      <article
        v-for="expert in expertOptions"
        :key="expert.expertId"
        class="expert-chip"
        :class="{ active: form.expertId === expert.expertId }"
        :title="`查看 ${expert.realName} 的介绍`"
        @click="goExpert(expert)"
      >
        <!-- ★ :src 必须接：expertAvatar() 优先用库里的 avatarUrl，为空才落到
             public/experts/ 的本地图，两级都有兜底。原先这里只有姓名首字，
             所以接口早就把 avatarUrl 发下来了，页面上却永远是「赵 / 孙 / 吴…」 -->
        <el-avatar :size="46" :src="expertAvatar(expert) || undefined">{{ expertInitial(expert.realName) }}</el-avatar>
        <div>
          <strong>{{ expert.realName }}</strong>
          <span>{{ expert.expertDomain }}</span>
        </div>
        <small>{{ expert.workloadStat }}</small>
      </article>
    </section>

    <div class="qa-layout">
      <el-card shadow="never" class="ask-card">
        <template #header>
          <div class="card-head">
            <div>
              <strong>我要提问</strong>
              <span>{{ askMode === 'ai' ? 'AI 即时给参考建议，不进入专家工作台' : '问题会进入专家工作台' }}</span>
            </div>
          </div>
        </template>

        <!-- ★ 问谁：由农户自己选。
             ★ AI 未启用（后端 /ai/status 返回 enabled:false）时这一整块**不渲染** ——
               宁可没有入口，也不要给一个点了就报错的按钮。这正是本页同一批改动里
               「落地页资讯看起来能点却点不动」那条教训的用法。 -->
        <el-radio-group v-if="aiEnabled" v-model="askMode" class="ask-mode">
          <el-radio-button value="expert">问平台专家</el-radio-button>
          <el-radio-button value="ai">问 AI 助手</el-radio-button>
        </el-radio-group>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <!-- 指定专家只在「问平台专家」时有意义：AI 模式没有指派对象 -->
          <el-form-item v-if="askMode === 'expert'" label="指定专家（可不选）" prop="expertId">
            <el-select v-model="form.expertId" clearable placeholder="不选择则由平台分派" style="width: 100%">
              <el-option v-for="expert in expertOptions" :key="expert.expertId" :label="`${expert.realName} · ${expert.expertDomain}`" :value="expert.expertId" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题描述" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="7"
              maxlength="500"
              show-word-limit
              :placeholder="askMode === 'ai'
                ? '例如：露地番茄坐果期叶子背面出现白粉状霉层，连续阴雨一周，该怎么办'
                : '请说明品种、当前现象、已采取的措施，以及希望专家重点判断的问题'"
            />
          </el-form-item>

          <!-- ★ 报错只在这一处渲染，且**不清空输入框**：AI 失败（超时/额度用完）时
               用户最需要的是「我刚才写的那段话还在」，而不是重打一遍。
               全局拦截器已经弹过一次 toast（err.notified），这里只补一条留在页面上的记录，
               不复读、不重复弹。 -->
          <el-alert
            v-if="askMode === 'ai' && aiError"
            class="ask-error"
            :title="aiError"
            type="warning"
            :closable="false"
            show-icon
          />

          <el-button
            type="primary"
            size="large"
            :loading="askMode === 'ai' ? aiAsking : submitting"
            style="width: 100%"
            @click="handleSubmit"
          >
            {{ askMode === 'ai' ? '问 AI 助手' : '提交问题' }}
          </el-button>
        </el-form>

        <div class="ask-tip">
          <strong>提问小技巧</strong>
          <p v-if="askMode === 'ai'">说明品种、生育期、当地天气和已经做过的处置，AI 的参考建议会更贴合你的地块</p>
          <p v-else>描述越具体，专家越容易给出可执行建议。涉及用药时，请同时说明当前生育期和近期天气</p>
        </div>
      </el-card>

      <section v-if="askMode === 'expert'" class="question-list-wrap">
        <div class="list-head">
          <div><h3>我的问答</h3><span>共 {{ filteredQuestions.length }} 条记录</span></div>
          <el-segmented v-model="statusFilter" :options="statusOptions" />
        </div>

        <div v-loading="loading" class="question-list">
          <article v-for="item in filteredQuestions" :key="item.questionId" class="question-card">
            <div class="question-top">
              <div class="questioner"><el-avatar :size="36">我</el-avatar><div><strong>我的提问</strong><span>{{ formatDateTime(item.askTime) }}</span></div></div>
              <el-tag :type="item.status === '已回复' ? 'success' : 'warning'">{{ item.status }}</el-tag>
            </div>
            <p class="question-content">{{ item.content }}</p>
            <div class="expert-target">提问对象：{{ getExpertName(item.expertId) }}</div>
            <div v-if="item.answers?.length" class="answer-list">
              <div v-for="answer in item.answers" :key="answer.answerId" class="answer-item">
                <!-- ★ 回答者头像同理：getExpert() 拿整条 ExpertVO 才能喂给
                     expertAvatar()。找不到该专家（如答案属已下架的专家）时，
                     两级兜底都空，自动回退到姓名首字，不会留碎图。 -->
                <el-avatar :size="36" class="expert-avatar" :src="expertAvatar(getExpert(answer.expertId)) || undefined">{{ getExpertName(answer.expertId).slice(0, 1) }}</el-avatar>
                <div>
                  <div class="answer-head"><strong>{{ getExpertName(answer.expertId) }} · 专家回复</strong><span>{{ formatDateTime(answer.answerTime) }}</span></div>
                  <p>{{ answer.content }}</p>
                </div>
              </div>
            </div>
            <el-alert v-else title="平台已受理，等待专家回复" type="warning" :closable="false" show-icon />
          </article>
          <el-empty v-if="!loading && !filteredQuestions.length" description="暂无问答记录，提交第一个问题吧" />
        </div>
      </section>

      <!-- AI 模式的右栏：只放「本次会话」的问答。
           ★ 刻意**不与「我的问答」混排**：那一边是数据库里的真实提问记录（t_question），
             这一边是模型即时生成的参考意见，两者的可信度与留存期都不同。
             放在一起会让人以为 AI 的回答也是平台留下的正式答复。 -->
      <section v-else class="ai-thread-wrap">
        <div class="list-head">
          <div>
            <h3>AI 助手问答</h3>
            <span>{{ aiThread.length ? `本次会话 ${aiThread.length} 轮` : '回答由模型即时生成' }}</span>
          </div>
          <el-button v-if="aiThread.length" link @click="clearAiThread">清空记录</el-button>
        </div>

        <div v-loading="aiAsking" element-loading-text="AI 正在思考…" class="ai-thread">
          <article v-for="item in aiThread" :key="item.id" class="ai-entry">
            <p class="ai-question"><span class="ai-badge ai-badge-question">我的提问</span>{{ item.question }}</p>
            <!-- white-space: pre-line 让模型输出的分段与换行保留下来。
                 模型按提示词固定输出「现象判断 / 处置建议 / 注意事项」三段，
                 若折叠成一个长句，这个结构就白设计了。 -->
            <div class="ai-answer">
              <span class="ai-badge">AI 助手</span>
              <p>{{ item.answer }}</p>
            </div>
          </article>
          <el-empty
            v-if="!aiThread.length && !aiAsking"
            description="还没有提问，试着描述一下地里的情况"
          />
        </div>

        <p class="ai-disclaimer">AI 回答由模型生成，仅供参考。涉及用药与用量的最终判断，请咨询当地农技站或平台专家</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ExpertAnswerPanel from '@/components/expert/ExpertAnswerPanel.vue'
import { askQuestion, getMyQuestions, getQuestionAnswers } from '@/api/expert'
import { askAi, getAiStatus } from '@/api/ai'
import { expertOptions, getExpert, getExpertName, useExperts } from '@/constants/experts'
import { expertAvatar, expertInitial } from '@/utils/imagery'
import { useUserStore } from '@/store/user'

/**
 * 页面用途：专家问答。
 * 接口文件：frontend-web/src/api/expert.js、frontend-web/src/api/ai.js
 * 数据来源：登录农户自己的 t_question 记录和对应的 t_answer 回答；
 *           AI 问答不落库，只在本次浏览器会话内保留（见下方 AI_THREAD_KEY）。
 *
 * ★ 两种提问方式由农户自己选（askMode）：
 *   - expert：走真实流程，问题进入专家工作台，回答落 t_answer；
 *   - ai    ：只调大模型拿一条参考意见，**不写库、不对外发布**。
 *   分开的原因不只是「谁回答」：两者的可信度、留存期、责任主体都不同，
 *   混在一起会让农户以为 AI 的回答也是平台留下的正式答复。
 */
const userStore = useUserStore()
const router = useRouter()
const isExpert = computed(() => userStore.profile?.roleName === '专家')
const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const questions = ref([])
const statusFilter = ref('全部')
const statusOptions = ['全部', '待回复', '已回复']
const form = reactive({ expertId: null, content: '' })

// ——— AI 助手 ———
/** 后端是否已启用 AI。false 时「问 AI 助手」这个选项整块不出现 */
const aiEnabled = ref(false)
const askMode = ref('expert')
const aiAsking = ref(false)
const aiThread = ref([])
const aiError = ref('')

/** AI 会话记录只存本次浏览器会话，且按用户分开 —— 同机换账号不继承上一个人的问答 */
const AI_THREAD_KEY = 'rxt_ai_thread_'
/** 只留最近若干轮：再多也翻不动，还要占 sessionStorage */
const AI_THREAD_MAX = 10
/** 回传给后端的历史轮数。服务端还会再收口一次，这里的值只影响流量，不影响安全 */
const AI_HISTORY_ROUNDS = 3

function aiThreadKey() {
  return `${AI_THREAD_KEY}${userStore.profile?.userId ?? 'anon'}`
}

function loadAiThread() {
  try {
    const raw = sessionStorage.getItem(aiThreadKey())
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.slice(-AI_THREAD_MAX) : []
  } catch {
    // 存坏了就当没有。会话记录是附加体验，不该因为它让整页报错
    return []
  }
}

function saveAiThread() {
  try {
    sessionStorage.setItem(aiThreadKey(), JSON.stringify(aiThread.value.slice(-AI_THREAD_MAX)))
  } catch {
    // 隐私模式或配额满时写入会抛异常。静默放弃：提问本身已经成功了，
    // 不该因为「没记住」再给用户报一个错
  }
}

const rules = {
  content: [
    { required: true, message: '请填写问题内容', trigger: 'blur' },
    { min: 2, max: 500, message: '问题内容需为 2 至 500 个字', trigger: 'blur' }
  ]
}

const repliedCount = computed(() => questions.value.filter((item) => item.status === '已回复').length)
const pendingCount = computed(() => questions.value.filter((item) => item.status === '待回复').length)
const filteredQuestions = computed(() => statusFilter.value === '全部' ? questions.value : questions.value.filter((item) => item.status === statusFilter.value))

/** 点专家卡 → 该专家的介绍页（路由 `expert/:id`，即 ExpertDetail.vue）。 */
function goExpert(expert) {
  if (expert?.expertId != null) router.push(`/expert/${expert.expertId}`)
}

async function loadQuestions() {
  loading.value = true
  try {
    const data = await getMyQuestions()
    const list = Array.isArray(data) ? data : data?.records || []
    questions.value = await Promise.all(list.map(async (item) => {
      if (item.status !== '已回复') return { ...item, answers: [] }
      try {
        const answerData = await getQuestionAnswers(item.questionId)
        return { ...item, answers: Array.isArray(answerData) ? answerData : [] }
      } catch {
        return { ...item, answers: [] }
      }
    }))
  } catch {
    // 不做静默假数据兜底，接口异常时显示空态并保留统一错误提示。
    questions.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 检查 AI 是否可用，决定「问 AI 助手」这个选项显不显示。
 *
 * ★ 失败即视为不可用：接口不存在（后端未升级）、未登录、未开启 —— 对用户来说都是同一件事
 *   「现在用不了」。`getAiStatus()` 带了 silent，所以这里不会弹出任何提示，
 *   页面只是安静地少一个选项。
 */
async function loadAiStatus() {
  try {
    const data = await getAiStatus()
    aiEnabled.value = Boolean(data?.enabled)
  } catch {
    aiEnabled.value = false
  }
  if (!aiEnabled.value) {
    askMode.value = 'expert'
    return
  }
  aiThread.value = loadAiThread()
}

function handleSubmit() {
  return askMode.value === 'ai' ? handleAskAi() : handleAsk()
}

/**
 * AI 提问。与「问专家」共用同一个校验与同一个输入框，只是出口不同。
 */
async function handleAskAi() {
  aiError.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const question = form.content.trim()
  aiAsking.value = true
  try {
    const data = await askAi({
      content: question,
      // ★ 只带**此前**的轮次，本轮由 content 单独传 —— 若把本轮也塞进 history，
      //   同一个问题会以 user 消息出现两次。
      history: aiThread.value
        .slice(-AI_HISTORY_ROUNDS)
        .flatMap((item) => [
          { role: 'user', content: item.question },
          { role: 'assistant', content: item.answer }
        ])
    })
    aiThread.value.push({
      id: `${Date.now()}-${aiThread.value.length}`,
      question,
      answer: data?.content || ''
    })
    saveAiThread()
    form.content = ''
  } catch (err) {
    // 全局拦截器已经弹过一次 toast（err.notified），这里只把话留在页面上。
    // ★ 刻意**不清空输入框**：AI 超时或额度用完时，用户最需要的是
    //   「我刚才写的那段话还在」，而不是重打一遍。
    aiError.value = err?.message || 'AI 助手暂时忙不过来，请稍后重试，或直接提交给专家'
  } finally {
    aiAsking.value = false
  }
}

function clearAiThread() {
  aiThread.value = []
  aiError.value = ''
  saveAiThread()
}

async function handleAsk() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await askQuestion({ expertId: form.expertId, content: form.content })
    ElMessage.success('问题已提交，专家回复后会显示在这里')
    form.content = ''
    await loadQuestions()
  } finally {
    submitting.value = false
  }
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

// 专家列表走接口（原先是 constants/experts.js 里写死的两位），
// 否则「指定专家提问」的下拉与回答区的姓名都会停在兜底名单上
const { load: loadExperts } = useExperts()
onMounted(() => {
  loadExperts()
  loadQuestions()
  // 与上面两个并行发起、互不依赖：AI 是否可用决定了这里多不多一个选项，
  // 而「我的问答」无论 AI 开没开都要照常拉
  loadAiStatus()
})
</script>

<style scoped>
.qa-page { color: var(--el-text-color-primary); }
.qa-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 30px; color: #fff; border-radius: 20px; background: linear-gradient(125deg, #174b2d, #2f8d5b); box-shadow: 0 18px 36px rgba(23, 75, 45, .16); }
.eyebrow { color: #d2efd9; font-size: 13px; }
.qa-hero h2 { margin: 10px 0 8px; font-size: 30px; }
.qa-hero p { margin: 0; color: rgba(255,255,255,.76); line-height: 1.7; }
.hero-metrics { display: grid; grid-template-columns: repeat(3, 88px); gap: 10px; }
.hero-metrics div { padding: 13px 8px; text-align: center; border: 1px solid rgba(255,255,255,.18); border-radius: 13px; background: rgba(255,255,255,.1); }
.hero-metrics strong, .hero-metrics span { display: block; }
.hero-metrics strong { font-size: 24px; }
.hero-metrics span { margin-top: 4px; color: rgba(255,255,255,.68); font-size: 12px; }
.expert-strip { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.expert-chip { display: flex; align-items: center; gap: 12px; padding: 15px; border: 1px solid var(--el-border-color-light); border-radius: 15px; background: #fff; cursor: pointer; transition: .2s; }
.expert-chip:hover, .expert-chip.active { border-color: var(--el-color-primary); box-shadow: 0 10px 24px rgba(47, 141, 91, .1); transform: translateY(-2px); }
.expert-chip > div { flex: 1; }
.expert-chip strong, .expert-chip span { display: block; }
.expert-chip span { margin-top: 4px; color: var(--el-color-primary); font-size: 12px; }
.expert-chip small { max-width: 170px; color: var(--el-text-color-secondary); font-size: 11px; line-height: 1.5; }
.qa-layout { display: grid; grid-template-columns: 350px minmax(0, 1fr); gap: 18px; align-items: start; }
.ask-card { position: sticky; top: 88px; border-radius: 16px; }
.card-head, .list-head, .question-top, .questioner, .answer-head { display: flex; align-items: center; }
.card-head, .list-head, .question-top, .answer-head { justify-content: space-between; gap: 12px; }
.card-head strong, .card-head span { display: block; }
.card-head span, .list-head span { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.ask-tip { margin-top: 18px; padding: 13px; border-radius: 12px; color: #7b5a20; background: #fff8e8; }
.ask-tip strong { font-size: 13px; }
.ask-tip p { margin: 7px 0 0; font-size: 12px; line-height: 1.65; }
.question-list-wrap { padding: 20px; border-radius: 16px; background: #fff; border: 1px solid var(--el-border-color-light); }
.list-head h3 { margin: 0; }
.question-list { display: grid; gap: 14px; min-height: 260px; margin-top: 16px; }
.question-card { padding: 17px; border: 1px solid var(--el-border-color-light); border-radius: 14px; background: linear-gradient(145deg, #fff, #fbfdfb); }
.questioner { gap: 9px; }
.questioner strong, .questioner span { display: block; }
.questioner span { margin-top: 3px; color: var(--el-text-color-secondary); font-size: 11px; }
.question-content { margin: 15px 0 12px; line-height: 1.75; }
.expert-target { color: var(--el-text-color-secondary); font-size: 12px; }
.answer-list { display: grid; gap: 10px; margin-top: 15px; }
.answer-item { display: flex; gap: 11px; padding: 14px; border-left: 3px solid var(--el-color-primary); border-radius: 11px; background: var(--el-color-primary-light-9); }
.answer-item > div { flex: 1; }
.answer-item p { margin: 8px 0 0; line-height: 1.7; }
.answer-head span { color: var(--el-text-color-secondary); font-size: 11px; }
.expert-avatar { flex: 0 0 auto; }

/* 问谁：两个等宽的按钮，占满提问卡的宽度，选择状态一眼可见 */
.ask-mode { display: flex; width: 100%; margin-bottom: 18px; }
.ask-mode :deep(.el-radio-button) { flex: 1; }
.ask-mode :deep(.el-radio-button__inner) { width: 100%; }
.ask-error { margin-bottom: 12px; }

/* AI 会话区：外壳与「我的问答」保持一致，内部按「提问在上、回答在下」排 */
.ai-thread-wrap { padding: 20px; border-radius: 16px; background: #fff; border: 1px solid var(--el-border-color-light); }
.ai-thread { display: grid; gap: 16px; min-height: 260px; margin-top: 16px; }
.ai-entry { padding: 16px 17px; border: 1px solid var(--el-border-color-light); border-radius: 14px; background: linear-gradient(145deg, #fff, #f7fbf8); }
.ai-question { display: flex; align-items: flex-start; gap: 8px; margin: 0; padding-bottom: 12px; font-weight: 600; line-height: 1.7; border-bottom: 1px dashed var(--el-border-color-lighter); }
/* 回答里的分段与换行必须保留：模型按提示词固定输出「现象判断 / 处置建议 / 注意事项」三段，
   折叠成一个长句的话，这个结构就白设计了 */
.ai-answer { margin-top: 14px; }
.ai-answer p { margin: 8px 0 0; line-height: 1.8; white-space: pre-line; }
.ai-badge { flex: 0 0 auto; padding: 2px 9px; border-radius: 999px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 12px; font-weight: 400; line-height: 1.7; }
.ai-badge-question { color: var(--el-text-color-secondary); background: var(--el-fill-color-light); }
/* 免责说明：AI 的涉农建议（尤其用药）出错是有实际后果的，这条不能省 */
.ai-disclaimer { margin: 16px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }

@media (max-width: 980px) { .qa-layout { grid-template-columns: 1fr; } .ask-card { position: static; } }
@media (max-width: 680px) { .qa-hero { align-items: flex-start; flex-direction: column; } .hero-metrics, .expert-strip { grid-template-columns: 1fr; width: 100%; } .expert-chip small { display: none; } }
</style>
