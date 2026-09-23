<template>
  <ExpertAnswerPanel v-if="isExpert" />

  <div v-else class="qa-page">
    <!-- 页面用途：农户查看自己的历史提问，选择平台专家并提交新的农业问题。 -->
    <section class="qa-hero">
      <div>
        <span class="eyebrow">专家在线答疑</span>
        <h2>有问题，交给懂农业的人</h2>
        <p>描述作物或养殖情况，选择擅长领域的专家，也可以让平台自动分派。</p>
      </div>
      <div class="hero-metrics">
        <div><strong>{{ questions.length }}</strong><span>我的提问</span></div>
        <div><strong>{{ repliedCount }}</strong><span>已回复</span></div>
        <div><strong>{{ pendingCount }}</strong><span>待回复</span></div>
      </div>
    </section>

    <section class="expert-strip">
      <article v-for="expert in expertOptions" :key="expert.expertId" class="expert-chip" :class="{ active: form.expertId === expert.expertId }" @click="selectExpert(expert)">
        <el-avatar :size="46">{{ expert.realName.slice(0, 1) }}</el-avatar>
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
            <div><strong>我要提问</strong><span>问题会进入专家工作台</span></div>
            <el-tag type="success" effect="plain">POST /api/expert/qa/ask</el-tag>
          </div>
        </template>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="指定专家（可不选）" prop="expertId">
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
              placeholder="请说明品种、当前现象、已采取的措施，以及希望专家重点判断的问题"
            />
          </el-form-item>
          <el-button type="primary" size="large" :loading="submitting" style="width: 100%" @click="handleAsk">
            提交问题
          </el-button>
        </el-form>

        <div class="ask-tip">
          <strong>提问小技巧</strong>
          <p>描述越具体，专家越容易给出可执行建议。涉及用药时，请同时说明当前生育期和近期天气。</p>
        </div>
      </el-card>

      <section class="question-list-wrap">
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
                <el-avatar :size="36" class="expert-avatar">{{ getExpertName(answer.expertId).slice(0, 1) }}</el-avatar>
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
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ExpertAnswerPanel from '@/components/expert/ExpertAnswerPanel.vue'
import { askQuestion, getMyQuestions, getQuestionAnswers } from '@/api/expert'
import { expertOptions, getExpertName } from '@/constants/experts'
import { useUserStore } from '@/store/user'

/**
 * 页面用途：专家问答。
 * 接口文件：frontend-web/src/api/expert.js
 * 数据来源：登录农户自己的 t_question 记录和对应的 t_answer 回答。
 */
const userStore = useUserStore()
const isExpert = computed(() => userStore.profile?.roleName === '专家')
const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const questions = ref([])
const statusFilter = ref('全部')
const statusOptions = ['全部', '待回复', '已回复']
const form = reactive({ expertId: null, content: '' })

const rules = {
  content: [
    { required: true, message: '请填写问题内容', trigger: 'blur' },
    { min: 2, max: 500, message: '问题内容需为 2 至 500 个字', trigger: 'blur' }
  ]
}

const repliedCount = computed(() => questions.value.filter((item) => item.status === '已回复').length)
const pendingCount = computed(() => questions.value.filter((item) => item.status === '待回复').length)
const filteredQuestions = computed(() => statusFilter.value === '全部' ? questions.value : questions.value.filter((item) => item.status === statusFilter.value))

function selectExpert(expert) {
  form.expertId = form.expertId === expert.expertId ? null : expert.expertId
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

onMounted(loadQuestions)
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
@media (max-width: 980px) { .qa-layout { grid-template-columns: 1fr; } .ask-card { position: static; } }
@media (max-width: 680px) { .qa-hero { align-items: flex-start; flex-direction: column; } .hero-metrics, .expert-strip { grid-template-columns: 1fr; width: 100%; } .expert-chip small { display: none; } }
</style>
