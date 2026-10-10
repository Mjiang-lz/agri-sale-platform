<template>
  <div class="expert-answer-page">
    <!-- 页面用途：专家登录后查看待回复问题并提交专业回答。 -->
    <section class="expert-hero">
      <div>
        <span class="eyebrow">专家工作台 · 在线答疑</span>
        <h2>{{ expert.realName }}，待回复问题都在这里</h2>
        <p>公开问题任何专家可认领，指定给你的问题只有你可以回复。提交后问题状态会自动更新为“已回复”</p>
      </div>
      <div class="hero-metric">
        <strong>{{ pendingQuestions.length }}</strong>
        <span>条待处理</span>
      </div>
    </section>

    <section class="expert-stat-grid">
      <article><span>待回复</span><strong>{{ pendingQuestions.length }}</strong><small>需要处理的提问</small></article>
      <article><span>公开提问</span><strong>{{ publicCount }}</strong><small>可认领问题</small></article>
      <article><span>指定给我</span><strong>{{ assignedCount }}</strong><small>专属咨询</small></article>
      <article><span>专业领域</span><strong class="domain-text">{{ expert.expertDomain }}</strong><small>当前服务方向</small></article>
    </section>

    <el-card shadow="never" class="work-card">
      <template #header>
        <div class="work-head">
          <div><strong>待回复问题</strong><span>按提问时间倒序</span></div>
          <el-button :loading="loading" @click="loadPending">刷新待办</el-button>
        </div>
      </template>

      <div v-loading="loading" class="question-grid">
        <article v-for="item in pendingQuestions" :key="item.questionId" class="question-card">
          <div class="question-head">
            <div class="asker"><el-avatar :size="38">问</el-avatar><span><strong>农户提问 #{{ item.askerId }}</strong><small>{{ formatDateTime(item.askTime) }}</small></span></div>
            <el-tag :type="item.expertId ? 'warning' : 'success'">{{ item.expertId ? '指定专家' : '公开提问' }}</el-tag>
          </div>
          <p class="question-content">{{ item.content }}</p>
          <div class="question-footer">
            <span>提问对象：{{ item.expertId ? getExpertName(item.expertId) : '平台分派 / 可认领' }}</span>
            <el-button type="primary" @click="openAnswer(item)">回复问题</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && !pendingQuestions.length" description="当前没有待回复问题" />
      </div>
    </el-card>

    <el-dialog v-model="answerVisible" title="填写专家回答" width="min(680px, 94vw)" destroy-on-close>
      <div v-if="currentQuestion" class="question-preview">
        <span>农户问题</span>
        <p>{{ currentQuestion.content }}</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="专家回答" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="7" maxlength="500" show-word-limit placeholder="请给出可执行的建议，并说明适用条件和注意事项" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="answerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAnswer">提交回答</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getExpertPendingQuestions, submitExpertAnswer } from '@/api/expert'
import { expertOptions, getExpertName } from '@/constants/experts'
import { useUserStore } from '@/store/user'

/**
 * 专家回答组件。
 * 接口：
 * - GET  /api/expert/qa/pending
 * - POST /api/expert/qa/answer
 */
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const answerVisible = ref(false)
const pendingQuestions = ref([])
const currentQuestion = ref(null)
const formRef = ref()
const form = reactive({ content: '' })
const rules = { content: [{ required: true, message: '请填写回答内容', trigger: 'blur' }, { min: 2, max: 500, message: '回答内容需为 2 至 500 个字', trigger: 'blur' }] }

const expert = computed(() => expertOptions.find((item) => item.userId === Number(userStore.profile?.userId)) || { realName: userStore.profile?.realName || '专家', expertDomain: '农业技术服务' })
const publicCount = computed(() => pendingQuestions.value.filter((item) => !item.expertId).length)
const assignedCount = computed(() => pendingQuestions.value.filter((item) => item.expertId).length)

async function loadPending() {
  loading.value = true
  try {
    const data = await getExpertPendingQuestions()
    pendingQuestions.value = Array.isArray(data) ? data : data?.records || []
  } finally {
    loading.value = false
  }
}

function openAnswer(question) {
  currentQuestion.value = question
  form.content = ''
  answerVisible.value = true
}

async function submitAnswer() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await submitExpertAnswer({ questionId: currentQuestion.value.questionId, content: form.content })
    answerVisible.value = false
    ElMessage.success('回答已提交，提问状态已更新')
    await loadPending()
  } finally {
    submitting.value = false
  }
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

onMounted(loadPending)
</script>

<style scoped>
.expert-answer-page { color: #24332b; }
.expert-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 30px; border-radius: 20px; color: #fff; background: linear-gradient(125deg, #173f2a, #2f8d5b); box-shadow: 0 18px 36px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }
.expert-hero h2 { margin: 10px 0 8px; font-size: 29px; }
.expert-hero p { margin: 0; max-width: 760px; color: rgba(255,255,255,.75); line-height: 1.7; }
.hero-metric { min-width: 125px; padding: 15px; text-align: center; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-metric strong, .hero-metric span { display: block; }
.hero-metric strong { font-size: 30px; }
.hero-metric span { margin-top: 4px; color: rgba(255,255,255,.68); font-size: 12px; }
.expert-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.expert-stat-grid article { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.expert-stat-grid span, .expert-stat-grid small { display: block; color: #75827a; }
.expert-stat-grid strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #26794f; }
.expert-stat-grid .domain-text { font-size: 17px; }
.work-card { border-radius: 16px; }
.work-head, .question-head, .asker, .question-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.work-head strong, .work-head span { display: block; }
.work-head span { margin-top: 4px; color: #75827a; font-size: 12px; }
.question-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; min-height: 240px; }
.question-card { padding: 18px; border: 1px solid #e1eae4; border-radius: 15px; background: linear-gradient(145deg, #fff, #fbfdfb); }
.asker { justify-content: flex-start; }
.asker strong, .asker small { display: block; }
.asker small { margin-top: 3px; color: #75827a; }
.question-content { min-height: 76px; margin: 16px 0; line-height: 1.75; }
.question-footer { padding-top: 13px; border-top: 1px solid #edf2ee; color: #75827a; font-size: 12px; }
.question-preview { margin-bottom: 18px; padding: 14px; border-radius: 12px; background: #f3faf4; }
.question-preview span { color: #75827a; font-size: 12px; }
.question-preview p { margin: 7px 0 0; line-height: 1.7; }
@media (max-width: 1000px) { .expert-stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) { .expert-hero, .work-head, .question-footer { align-items: stretch; flex-direction: column; } .hero-metric { width: 100%; } .expert-stat-grid, .question-grid { grid-template-columns: 1fr; } }
</style>
