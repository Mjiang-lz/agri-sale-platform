<template>
  <div class="knowledge-detail-page">
    <!--
      页面用途：单篇农业知识的独立阅读页（路由 /knowledge/:id）。

      ★ 2026-10-12 由「列表页里的弹窗」改成独立页面。用户的要求是
      「每个帖子知识新起一个页面」—— 弹窗形式的问题不只是观感：
        · 没有自己的地址，读完一篇没法把链接发给别人；
        · 浏览器的前进/后退对它无效，误关之后回不到那一篇；
        · 落地时顶栏高亮会切到「专家助力」（旧实现是跳到 /expert/knowledge 开弹窗），
          从首页点知识却进了另一个板块，归属感是错的。
    -->
    <div class="page-head">
      <el-button link @click="goList">← 返回农技知识</el-button>
    </div>

    <el-skeleton v-if="loading" animated :rows="10" class="article-skeleton" />

    <!-- 404 与「加载失败」必须分开：前者重试一万次也不会成功，给重试按钮是误导 -->
    <el-result
      v-else-if="status === 'missing'"
      class="article-result"
      icon="warning"
      title="这条知识不存在或未通过审核"
      sub-title="它可能已被作者删除，或仍在审核队列中。"
    >
      <template #extra>
        <el-button type="primary" @click="goList">去看看其他农技知识</el-button>
      </template>
    </el-result>

    <el-alert
      v-else-if="status === 'error'"
      type="error"
      show-icon
      :closable="false"
      title="知识内容加载失败"
      :description="errorMessage"
      class="error-alert"
    >
      <template #default>
        <div class="error-actions">
          <el-button type="danger" size="small" @click="load">重新加载</el-button>
          <el-button size="small" @click="goList">返回列表</el-button>
        </div>
      </template>
    </el-alert>

    <article v-else-if="knowledge" class="article">
      <header class="article-head">
        <el-tag effect="plain" round>{{ knowledge.category }}</el-tag>
        <h1>{{ knowledge.title }}</h1>
        <div class="article-meta">
          <span>发布时间：{{ formatDateTime(knowledge.createTime) }}</span>
          <span>浏览 {{ knowledge.viewCount ?? 0 }}</span>
        </div>
      </header>

      <!--
        配图用 contain 不用 cover：知识配图可能是竖图（实测有一张 810×1440），
        cover 会把竖图裁成中间一条，读者看不到全貌。
        容器居中 + 限高，横图竖图都不会把页面撑坏；点图可放大看细节。

        ★ 2026-10-12 修：原来 v-if 只看 `knowledge.imageUrl`，于是出现
        「首页卡片有图、点进来却没有」的割裂 —— 首页卡片走的是 `knowledgeImage()`
        三级兜底，而详情页只认第一级（数据库字段），可库里 19 条有 18 条该字段是 NULL。
        现在两边同源：都用 `knowledgePhoto()`，作者传了图就用作者的，
        没有则退到标题命中的实拍主题图。
        但**不含**分类渐变封面那一级 —— 渐变块在文章页没有信息量，
        配「点击图片可放大」更荒谬，所以没有真图就干脆不显示配图块。
      -->
      <div v-if="photo" class="cover-box">
        <el-image
          class="cover-image"
          :src="photo"
          :preview-src-list="[photo]"
          preview-teleported
          fit="contain"
          @error="coverFailed = true"
        />
        <span class="cover-tip">点击图片可放大查看</span>
      </div>

      <el-divider />

      <!-- 正文按原文换行渲染：农技文章里「1. 2. 3.」这种分行是有信息量的 -->
      <div class="article-content">{{ knowledge.content }}</div>

      <el-alert
        class="content-tip"
        type="info"
        :closable="false"
        show-icon
        title="内容说明"
        description="农业生产建议仅供参考，请结合当地气候、土壤和作物实际情况执行；涉及用药请遵守安全间隔期。"
      />

      <footer class="article-foot">
        <el-button @click="goList">← 返回农技知识列表</el-button>
      </footer>
    </article>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeDetail } from '@/api/knowledge'
import { knowledgeKeywordPhoto, knowledgePhoto } from '@/utils/imagery'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const knowledge = ref(null)

/** 配图是否加载失败（OBS 取不到：断网 / 对象被删） */
const coverFailed = ref(false)

/**
 * 配图（作者上传的 或 标题命中的实拍主题图）。
 * 没有真图时为空串 ⇒ 模板整个配图块不渲染，而不是退化成一张渐变横幅。
 *
 * 加载失败时退到 `knowledgeKeywordPhoto()`（本地实拍图），**不再退到渐变封面** ——
 * 渐变块在文章里只是噪音，退到它等于把上面那条规则绕开了；
 * 本地图也没有（抽象主题）就干脆隐藏配图块，与「本来就没真图」表现一致。
 *
 * ⚠️ 回退必须跳过第 ① 级，否则 `knowledgePhoto()` 又会返回那个坏掉的 OBS 地址。
 */
const photo = computed(() => {
  const k = knowledge.value
  if (!k) return ''
  return coverFailed.value ? knowledgeKeywordPhoto(k) : knowledgePhoto(k)
})
const errorMessage = ref('')
/** 'ok' | 'missing'（404，重试无意义）| 'error'（可重试） */
const status = ref('ok')

async function load() {
  const id = route.params.id
  knowledge.value = null
  errorMessage.value = ''
  status.value = 'ok'
  coverFailed.value = false

  if (!id) {
    loading.value = false
    status.value = 'missing'
    return
  }

  loading.value = true
  try {
    const data = await getKnowledgeDetail(id)
    if (data) {
      knowledge.value = data
    } else {
      status.value = 'missing'
    }
  } catch (err) {
    // 后端详情接口只返回「已通过」的知识，查不到时给 404 —— 那是终态，不是故障
    status.value = err?.code === 404 ? 'missing' : 'error'
    errorMessage.value = err?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 回到知识列表。注意列表页在 /expert/knowledge（农技知识目前挂在「专家助力」菜单下） */
function goList() {
  router.push('/expert/knowledge')
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(load)

// 同一路由不同参数时组件会被复用（比如从别处再点开另一篇），
// 只靠 onMounted 不会重新拉数据，页面会停在上一篇上 —— 与 ProductDetail 同口径。
watch(
  () => route.params.id,
  (next, prev) => {
    if (next !== prev) load()
  }
)
</script>

<style scoped>
/* 阅读型页面：限宽居中。正文行长超过 900px 之后眼睛要来回扫，反而难读 */
.article { max-width: 900px; margin: 0 auto; padding: 28px 32px 34px; border: 1px solid #e1eae4; border-radius: 16px; background: #fff; }
.article-skeleton, .article-result { max-width: 900px; margin: 0 auto; }
.page-head { max-width: 900px; margin: 0 auto 14px; }
.article-head { text-align: center; }
.article-head h1 { margin: 12px 0 10px; font-size: 26px; line-height: 1.45; color: #1f3b2c; }
.article-meta { display: flex; justify-content: center; gap: 18px; color: #75827a; font-size: 13px; }
.cover-box { display: flex; flex-direction: column; align-items: center; gap: 8px; margin-top: 22px; }
.cover-image { max-width: 640px; height: 340px; border: 1px solid #e1eae4; border-radius: 12px; background: #f7faf8; cursor: zoom-in; }
.cover-tip { color: #98a49c; font-size: 12px; }
.article-content { white-space: pre-wrap; line-height: 1.95; font-size: 15.5px; color: #2b3a32; }
.content-tip { margin-top: 26px; }
.article-foot { display: flex; justify-content: center; margin-top: 22px; }
.error-actions { display: flex; gap: 10px; margin-top: 10px; }
@media (max-width: 760px) {
  .article { padding: 20px 18px 26px; }
  .cover-image { max-width: 100%; height: 260px; }
  .article-meta { flex-direction: column; gap: 4px; }
}
</style>
