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
      <!--
        ★ 2026-10-12 第三轮调整（用户：「图片不用占据这么多空间，正文才是主导位置」）
        标题区从「居中 + 三行」压成「左对齐 + 两行」：
        分类标签与时间/浏览并到同一行，标题跟在下面。原来光标题区就吃掉约 130px，
        加上配图，正文被挤到首屏之外 —— 阅读页的标题本来就该左对齐（居中会读成海报）。
      -->
      <header class="article-head">
        <div class="head-meta">
          <el-tag effect="plain" round size="small">{{ knowledge.category }}</el-tag>
          <span>发布时间：{{ formatDateTime(knowledge.createTime) }}</span>
          <span>浏览 {{ knowledge.viewCount ?? 0 }}</span>
        </div>
        <h1>{{ knowledge.title }}</h1>
      </header>

      <!--
        正文优先：紧接标题，不再被配图挡在后面（农技文章里「1. 2. 3.」这类分行有信息量，
        所以用 pre-wrap 按原文渲染）。
      -->
      <div class="article-content">{{ knowledge.content }}</div>

      <!--
        配图下沉为「附图」，放在正文之后。三条理由：
          ① 正文优先 —— 读者进页面第一眼就是内容，而不是先划过一张图；
          ② 列表卡片上已经有同一张封面了，详情页再顶一张大图属于重复；
          ③ 「点开大图」本来就是按需动作，想看细节的人点一下即可，不想看的人不被打断。
        尺寸仍由 onCoverLoad 算出的 aspect-ratio 决定，这里只封顶（见下 CSS）。

        ★ 前一版的两处修正（同日）：
          · 原 `v-if` 只看 `knowledge.imageUrl`，导致「首页卡片有图、点进来却没有」的割裂
            → 改用 `knowledgePhoto()`，与卡片同源；但**不含**分类渐变封面那一级，
              渐变块在文章里没有信息量，没有真图就干脆不显示配图。
          · 原 `max-width: 640px; height: 340px` 的**固定盒子**（EP 把内部 img 强制成
            `width:100%;height:100%`，盒子尺寸决定观感）会把 810×1440 的竖图做成
            「图小盒大」的假留白 → 改成盒子贴图。
      -->
      <figure v-if="photo" class="cover-box">
        <el-image
          class="cover-image"
          :style="coverStyle"
          :src="photo"
          :preview-src-list="[photo]"
          preview-teleported
          fit="contain"
          @load="onCoverLoad"
          @error="coverFailed = true"
        />
        <figcaption class="cover-tip">附图 · 点击可放大查看</figcaption>
      </figure>

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

/**
 * 配图的真实宽高比，形如 `"810 / 1440"`。空串表示还没量到（走默认值）。
 *
 * 为什么要在 JS 里量：`el-image` 内部 `img` 被 EP 强制 `width:100%;height:100%`，
 * 所以只能反过来让**盒子**去适配图 —— 而 CSS 里没有「按图片自身比例收缩的盒子」这种
 * 自洽写法（`width:auto` 的父盒子拿不到子元素被 max-height 压过之后的实际宽度，
 * 容易退回成一个宽而空的盒子，正是这次要修的毛病）。
 * 加载完读一次 naturalWidth/naturalHeight 是最直接、也最可验证的做法。
 */
const coverAR = ref('')

/** 量到之前先按站内知识素材的通用比例（900×396 ≈ 2.27:1）占位，避免加载期高度跳变 */
function onCoverLoad(e) {
  const img = e?.target
  const w = Number(img?.naturalWidth)
  const h = Number(img?.naturalHeight)
  if (w > 0 && h > 0) coverAR.value = `${w} / ${h}`
}

const coverStyle = computed(() => ({ aspectRatio: coverAR.value || '900 / 396' }))
const errorMessage = ref('')
/** 'ok' | 'missing'（404，重试无意义）| 'error'（可重试） */
const status = ref('ok')

async function load() {
  const id = route.params.id
  knowledge.value = null
  errorMessage.value = ''
  status.value = 'ok'
  coverFailed.value = false
  coverAR.value = ''

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
.article { max-width: 900px; margin: 0 auto; padding: 26px 32px 30px; border: 1px solid #e1eae4; border-radius: 16px; background: #fff; }
.article-skeleton, .article-result { max-width: 900px; margin: 0 auto; }
.page-head { max-width: 900px; margin: 0 auto 14px; }
/*
 * 标题区：左对齐 + 分类与时间同排。
 * 改前是「居中 + 分类/标题/时间三行」，光这块就约 130px；正文被挤到首屏之外，
 * 观感变成「标题 + 配图」在撑场。阅读页标题左对齐也更符合习惯（居中会读成海报）。
 */
.article-head { text-align: left; }
.head-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; color: #75827a; font-size: 13px; }
.article-head h1 { margin: 10px 0 0; font-size: 24px; line-height: 1.45; color: #1f3b2c; }
/* 正文：紧跟标题，是这一页的主体 */
.article-content { margin-top: 18px; white-space: pre-wrap; line-height: 1.95; font-size: 15.5px; color: #2b3a32; }
/*
 * 附图（正文之后）：尺寸由 style 里动态算出的 `aspect-ratio` 决定（见 onCoverLoad），
 * 这里只封顶 —— 高度不超过 200px、宽度不超过正文列宽。
 * 两轴都封顶后 `contain` 恰好等于图片本身，盒子边缘即图片边缘，没有假留白。
 * 用一条极细的分隔线代替 el-divider：后者上下各 24px，纯浪费纵向空间。
 */
.cover-box { display: flex; flex-direction: column; align-items: center; gap: 8px; margin: 26px 0 0; padding-top: 20px; border-top: 1px solid #eef3ef; }
.cover-image { max-width: 100%; max-height: 200px; border: 1px solid #e1eae4; border-radius: 12px; overflow: hidden; background: #f7faf8; cursor: zoom-in; }
.cover-tip { color: #98a49c; font-size: 12px; }
.content-tip { margin-top: 24px; }
.article-foot { display: flex; justify-content: center; margin-top: 22px; }
.error-actions { display: flex; gap: 10px; margin-top: 10px; }
@media (max-width: 760px) {
  .article { padding: 20px 18px 26px; }
  .cover-image { max-height: 170px; }
  .head-meta { gap: 8px; }
}
</style>
