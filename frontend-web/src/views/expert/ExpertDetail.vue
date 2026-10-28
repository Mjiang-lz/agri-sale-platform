<template>
  <div class="expert-detail-page">
    <PageBackTop />

    <div v-if="loading" class="ed-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <el-result
      v-else-if="errorText"
      icon="warning"
      title="打不开这位专家的介绍"
      :sub-title="errorText"
    >
      <template #extra>
        <el-button type="primary" @click="goBack">返回专家列表</el-button>
      </template>
    </el-result>

    <template v-else-if="expert">
      <!-- ---------- 顶部：头像 + 姓名 + 领域 + 主行动 ---------- -->
      <section class="ed-hero">
        <div class="ed-avatar">
          <el-avatar :size="96" :src="avatarSrc">
            {{ expertInitial(expert.realName) }}
          </el-avatar>
        </div>
        <div class="ed-hero-main">
          <div class="ed-hero-title">
            <h2>{{ expert.realName }}</h2>
            <el-tag type="success" effect="plain">{{ expert.expertDomain }}</el-tag>
          </div>
          <p class="ed-hero-intro">{{ expert.introduction }}</p>
        </div>
        <div class="ed-hero-act">
          <el-button type="primary" size="large" @click="goBook">预约该专家</el-button>
          <el-button size="large" @click="goAsk">向他提问</el-button>
        </div>
      </section>

      <!-- ---------- 服务数据：全部来自后端实时统计 ---------- -->
      <section class="ed-stats">
        <div class="ed-stat">
          <strong>{{ expert.answeredCount ?? 0 }}</strong>
          <span>已答复提问</span>
        </div>
        <div class="ed-stat">
          <strong>{{ expert.askedCount ?? 0 }}</strong>
          <span>被指定提问</span>
        </div>
        <div class="ed-stat">
          <strong>{{ expert.appointmentCount ?? 0 }}</strong>
          <span>累计预约</span>
        </div>
        <div class="ed-stat">
          <strong>{{ expert.finishedCount ?? 0 }}</strong>
          <span>已完成服务</span>
        </div>
      </section>

      <div class="ed-cols">
        <!-- ---------- 他答复过的问题 ---------- -->
        <section class="ed-card">
          <h3>他答复过的问题</h3>
          <p class="ed-card-sub">下面是他已经给出答复的提问原文摘要，同类问题可以直接参考</p>
          <ul v-if="answers.length" class="ed-answers">
            <li v-for="(a, i) in answers" :key="i">
              <p>{{ a.content }}</p>
              <time>{{ a.askTime }}</time>
            </li>
          </ul>
          <el-empty v-else description="这位专家还没有公开答复记录" :image-size="72" />
        </section>

        <!-- ---------- 高频咨询主题 ---------- -->
        <section class="ed-card">
          <h3>找他咨询什么</h3>
          <p class="ed-card-sub">来自预约主题里出现两次以上的说法，看得出他擅长哪类问题</p>
          <div v-if="topics.length" class="ed-topics">
            <el-tag v-for="t in topics" :key="t" type="warning" effect="plain">{{ t }}</el-tag>
          </div>
          <el-empty v-else description="还没有足够的预约记录" :image-size="72" />
        </section>
      </div>

      <div class="ed-actions">
        <el-button @click="goBack">返回专家列表</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExpertDetail } from '@/api/expert'
import { expertAvatar, expertInitial } from '@/utils/imagery'
import PageBackTop from '@/components/PageBackTop.vue'

/**
 * 专家详细介绍页（需求 9）。
 *
 * 为什么必须有这一页：用户在专家列表上是**照着自己的难题挑人**的
 * （「地里的柑橘该打什么药」「果子卖不掉能存成干货吗」）。
 * 列表卡那一行履历回答不了这个问题，用户看完仍然不知道这位能不能
 * 解决自己的困难，于是要么随便约第一位、要么干脆不约。
 *
 * 所以这一页把「基础资料 + 已答复的问题原文 + 高频咨询主题」摊开，
 * 让用户拿自己的问题去对号入座。
 *
 * ★ 四个统计数字来自后端**实时聚合**，没有拿 workloadStat 顶 ——
 *   库里那句「已回复提问 42 条」是手写文案，与真实条数对不上，
 *   用户点进来一对就露馅，不如不写。
 */
const route = useRoute()
const router = useRouter()

const expert = ref(null)
const loading = ref(true)
const errorText = ref('')

const answers = computed(() => expert.value?.recentAnswers || [])
const topics = computed(() => expert.value?.appointmentTopics || [])
const avatarSrc = computed(() => expertAvatar(expert.value))

async function load(id) {
  loading.value = true
  errorText.value = ''
  expert.value = null
  try {
    expert.value = await getExpertDetail(id)
  } catch (e) {
    // 提示口径跟着后端走：未过审的专家与不存在的专家，后端返回的是同一句
    errorText.value = e?.message || '专家不存在或尚未通过资格认证'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}
function goBook() {
  router.push({ path: '/expert/appointment', query: { expertId: expert.value.expertId } })
}
function goAsk() {
  router.push({ path: '/expert/qa', query: { expertId: expert.value.expertId } })
}

onMounted(() => load(route.params.id))
// ★ 换 :id 必须重载：详情页不发请求就没法自己知道 id 变了，
//   Vue Router 复用同一个组件实例，onMounted 不会再跑一次
watch(() => route.params.id, (id) => { if (id) load(id) })
</script>

<style scoped>
.expert-detail-page { color: var(--el-text-color-primary); }

.ed-loading { padding: 24px; }

.ed-hero {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 26px 28px;
  background: var(--el-bg-color);
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
}
.ed-avatar { flex: none; }
.ed-hero-main { flex: 1; min-width: 0; }
.ed-hero-title {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.ed-hero-title h2 { margin: 0; font-size: 24px; }
.ed-hero-intro {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
  max-width: 70ch;
}
.ed-hero-act {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ed-stats {
  display: flex;
  gap: 14px;
  margin-top: 16px;
}
.ed-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
}
.ed-stat strong {
  font-size: 26px;
  color: var(--el-color-primary);
  line-height: 1;
}
.ed-stat span { font-size: 12px; color: var(--el-text-color-secondary); }

.ed-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}
@media (max-width: 980px) {
  .ed-cols { grid-template-columns: 1fr; }
  .ed-hero { flex-wrap: wrap; }
}

.ed-card {
  padding: 20px 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
}
.ed-card h3 { margin: 0; font-size: 16px; }
.ed-card-sub {
  margin: 8px 0 16px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.ed-answers { margin: 0; padding: 0; list-style: none; }
.ed-answers li {
  padding: 12px 0;
  border-top: 1px solid var(--rxt-border);
}
.ed-answers li:first-child { border-top: 0; }
.ed-answers p {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
.ed-answers time {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ed-topics { display: flex; flex-wrap: wrap; gap: 10px; }

.ed-actions { margin-top: 16px; text-align: center; }
</style>
