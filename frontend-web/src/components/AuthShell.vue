<template>
  <!--
    登录 / 注册共用的页面外壳：整屏底图 + 右侧抽屉式表单盒。

    为什么抽出来：登录页与注册页除了表单内容之外完全同构（底图、抽屉尺寸、
    圆角、阴影、品牌标、页脚）。各自写一份的话，这两页的视觉一定会慢慢漂开
    —— 全站 .page-head 被复制 9 份就是这个剧本。

    版式（2026-10-13 按要求调整）：底图铺满整屏，表单盒不再居中，
    改为**贴右侧的抽屉**，盒体半透明 40%。宽度经用户三轮微调
    （40% → 45% → **42%**），当前写在下面 .auth-drawer 的 width 上，
    这里不复述具体数字（改版式时最容易漏改注释）。
    这样左侧留出完整的油菜花田与人物，视觉重心在图上，表单只占右侧一条。

    ★ 版式又调过一版（"图标放头部、别分开、右侧铺满"）：
    之前标识在左下角、页脚在右下角，各自垫一块小牌子 —— 结果是
    「一条右侧面板上散着两个孤立方块」，盒子感被切碎了。
    现在改成**右侧一整块通栏盒子**：标识在盒内头部、表单占中间、
    页脚沉底，从上到下铺满整屏。左侧只留纯底图。

    ★ 页脚返回项按当前页分流（见脚本里的 footBack）：
    登录页回首页；注册页不放链接（表单下方已有「去登录」，重复了）。

    ★ 半透明带来的代价：油菜花田是高饱和黄绿，40% 透明度下
    表单文字会糊在花纹上。所以**输入框自己补了一层近不透明底**，
    只有盒子的留白区透图 —— 这是「盒子透、控件不透」的分层处理，
    不要为了整体统一把输入框也调成半透明，那样没法用。

    用法：<AuthShell><h1 class="auth-title">…</h1>…表单…</AuthShell>
    标题/表单用下面导出的类名（.auth-title / .auth-sub / .auth-switch / .auth-link /
    .auth-submit），它们写在本组件的非作用域块里，所以插槽内容也能吃到。
  -->
  <div class="auth-page" :style="{ '--auth-field': fieldUrl }">
    <!-- ★ 右侧通栏盒子：标识在头部、表单在中间、页脚沉底，三段共用一块底 -->
    <aside class="auth-drawer">
      <header class="auth-brand">
        <RxtLogo :size="44" />
        <div class="auth-brand-text">
          <strong>融销通</strong>
          <small>农产品融销一体平台</small>
        </div>
      </header>

      <main class="auth-card">
        <slot />
      </main>

      <footer class="auth-foot">
        <!-- ★ 注册页这里不放链接（返回 null）：
             表单下方已经有「已有账号？去登录」，页脚再放一个「返回登录页」
             是同一个动作出现两次。详见脚本里 footBack 的注释。 -->
        <router-link v-if="footBack" class="auth-link" :to="footBack.to">{{ footBack.text }}</router-link>
        <span>基于数字经济的农产品融销一体平台</span>
      </footer>
    </aside>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import RxtLogo from '@/components/RxtLogo.vue'

// ★ public/ 下的图必须跟 BASE_URL 走（web 端构建期是 /app/），写死 /auth/… 会 404
//
// ★ 这里必须在 JS 里就把 url() 包好，不能只把裸路径塞进 CSS 变量：
//   background-image 的图层语法不接受裸路径（只有 url() 或 gradient），
//   写成 `background: var(--auth-field) …` 整条声明会被**静默丢弃** ——
//   页面照常渲染、computed background-image 只是 none，看不出任何报错。
//   长写法 background-image/size/attachment 同样丢，别以为换长写法能绕开。
const fieldUrl = computed(() => `url("${import.meta.env.BASE_URL}auth/auth-field.jpg")`)

/* ★ 页脚的返回项按当前页分流：
 *   登录页 → 「返回首页」/home。还没登录，没有别的地方可去。
 *   注册页 → **不显示链接**（返回 null）。注册页表单下方已经有
 *              「已有账号？去登录」，页脚再放一个「返回登录页」是
 *              同一个动作出现两次；两个一模一样的入口反而让用户
 *              以为是两个不同的东西。中间那一段标语留着，
 *              免得页脚只剩一行字显得太空。
 *
 * 演绎过程（别当成最终方案）：最初两页都「返回首页」→ 用户要求注册页
 * 改「返回登录页」→ 用户又指出与「去登录」重复。**同一处位置反复被否，
 * 说明真正该问的是「这里到底需不需要一个链接」，而不是换一个目标。**
 *
 * 刻意在这里读路由自己判断，而不是让 Login/Register 各传一个 prop：
 * 两页共用同一个外壳、页脚位置与样式也完全相同，多一个 prop 就多一处
 * 「以后新增第三页忘了传」的坑。 */
const route = useRoute()
const footBack = computed(() => (route.path === '/register' ? null : { text: '返回首页', to: '/home' }))
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
  box-sizing: border-box;
  overflow-x: hidden;
  /* 油菜花田铺满整屏。
     罩层刻意做薄：厚了纹理会糊成一片，等于白墙；因为表单已经收到
     右侧抽屉里，左侧大片留白处不需要厚罩，只需要压住天空的高光，
     免得品牌标识白底看不清 */
  background:
    linear-gradient(90deg, rgba(14, 122, 95, 0.18) 0%, rgba(14, 122, 95, 0.06) 40%, transparent 62%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.14) 0%, transparent 30%),
    var(--auth-field) center / cover no-repeat;
  background-color: var(--rxt-page-bg);
}

/* ★ 右侧通栏盒子：品牌标识在头部、表单在中间、页脚沉底。
   半透明底与 backdrop-filter 都放在这里 —— 盒子只有这一块，
   标识/表单/页脚三段共用同一层底，所以它们天然是「一体的」。

   通栏的关键在 min-height:100vh + 三段用 flex 分配：
   头部与页脚 flex:0 0 auto 贴着上下边，中间 .auth-card 撑开剩余高度
   并自己居中，于是盒子在任何视口高度下都是满的。 */
.auth-drawer {
  position: relative;
  z-index: 1;
  width: 42%;
  min-width: 420px;
  /* ★ 42% 在 1440px 视口下是 605px，比 max-width 小得多，
     留 760px 是给更宽的屏留余量。**width 与 max-width 有交叉** ——
     上限比目标宽度小时，实测会顶到 max-width 而非 width%，
     数字改了而比值对不上。判宽度一律量 getBoundingClientRect 算比值 */
  max-width: 760px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  /* 盒子本体：透明度 40%（需求指定）+ 背景模糊。
     单靠 40% 透明度，花田的高频黄绿纹理会直接透过文字底下、字会花掉，
     所以模糊是这条需求的**必要配套**，不是装饰 */
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(14px) saturate(115%);
  -webkit-backdrop-filter: blur(14px) saturate(115%);
  /* 左缘一条发丝线，把盒子与底图分开。
     不要用 box-shadow 横向偏移 —— 那会连右侧一起投出去，不像「一条线」 */
  border-left: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: -18px 0 48px rgba(20, 52, 42, 0.12);
}

/* 头部：品牌标识。flex:0 0 auto —— 不参与剩余高度分配，永远贴顶 */
.auth-brand {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 34px 32px 22px;
}
.auth-brand-text strong,
.auth-brand-text small {
  display: block;
}
.auth-brand-text strong {
  font-size: 23px;
  letter-spacing: 1px;
  /* ★ 半透明底上的深色字要够「黑」才够清晰。--rxt-ink 是带绿调的
     灰墨（#14302b），在半透明白 + 花田背景上偏灰；这里压到接近纯墨色 */
  color: #0d201b;
}
.auth-brand-text small {
  margin-top: 3px;
  font-size: 12px;
  color: #33544a;
}

/* 中间：表单。flex:1 撑开剩余高度并把内容居中 */
.auth-card {
  position: relative;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 100%;
  padding: 8px 32px 24px;
  box-sizing: border-box;
}

/* 底部：页脚。同样 flex:0 0 auto，永远沉底 */
.auth-foot {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 20px 32px 28px;
  /* ★ 页脚与表单共用同一块半透明底，不再自己垫白牌：
     之前它单独垫一块牌子，底色跟盒子一深一浅，反而把盒子切成了三段 */
  font-size: 13px;
  /* ★ 半透明底上的灰字偏淡偏小，压深一档才够清晰 */
  color: #16302a;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.7);
}
.auth-foot .auth-link {
  font-weight: 600;
}

/* ★ 窄屏（平板竖屏 / 手机）：40% + min-width:420px 在手机视口必然溢出，
   一旦出现横向滚动条整页构图就废了 —— 改成满宽通栏。
   底图同时退场：满宽之后底图完全被盒子盖住，留着只是白白占解码带宽；
   而且半透明底 + 模糊在没有对比图案时也没意义。 */
@media (max-width: 900px) {
  .auth-page {
    background: var(--rxt-page-bg);
  }
  .auth-drawer {
    width: 100%;
    min-width: 0;
    max-width: none;
    /* 满宽时不再有左右之分，横向发丝线与投影都去掉，
       否则左侧会出现一条莫名其妙的竖线 */
    border-left: none;
    box-shadow: none;
    /* 底图退场后没有东西可透，模糊一并关掉（backdrop-filter 在
       无图案时会让文字边缘发虚，实测比不开更糊） */
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
    /* 满宽时不透明 —— 半透明配纯色底 = 灰扑扑一片，比不透明更难看 */
    background: rgba(255, 255, 255, 0.96);
  }
  /* 头部标识居中，窄屏下左侧对齐会显得歪 */
  .auth-brand {
    justify-content: center;
    padding: 28px 20px 18px;
  }
  .auth-card {
    padding: 8px 20px 20px;
  }
  .auth-foot {
    padding: 18px 20px 24px;
  }
}
</style>

<!--
  下面这组是「插槽内容」用的类，必须放在**非 scoped** 块里：
  scoped 只会给本组件模板里的元素打 data-v 标记，而标题与表单是父组件
  送进来的插槽内容，带的是父组件的标记 —— 写在 scoped 里父组件吃不到。
  这些名字都以 auth- 开头，碰撞面很小。
-->
<style>
.auth-title {
  margin: 26px 0 0;
  /* ★ 字号与字重一起提：半透明底 + 背景模糊会把字边缘吃掉，
     20px/600 在这套底上偏「飘」。24px/700 才压得住 */
  font-size: 24px;
  font-weight: 700;
  color: #0d201b;
}
.auth-sub {
  margin: 8px 0 22px;
  font-size: 14px;
  line-height: 1.7;
  color: #33544a;
}

/*
  没有副标题时的标题（登录页 2026-10-10 去掉了副标题）。
  标题到表单的下间距原本由 .auth-sub 的 margin-bottom 撑着，
  副标题一没，表单就会贴住标题 —— 这里把那段间距接下来。
  ★ 不去改 .auth-title 本身：注册页还有副标题，改了它间距会翻倍。
*/
.auth-title-solo {
  margin-bottom: 22px;
}

/* 主操作按钮撑满卡片；高度交给 EP 的 size="large"，不手写 px */
.auth-submit .el-form-item__content {
  display: block;
}
.auth-submit .el-button {
  width: 100%;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 4px;
  /* 按钮在花田背景上，中间偏右有一片高饱和黄绿；
     给白字压一层同色深影，白字才不会「陷进背景里」 */
  text-shadow: 0 1px 2px rgba(6, 62, 48, 0.45);
}

.auth-switch {
  margin-top: 4px;
  text-align: center;
  font-size: 15px;
  color: #26453c;
}
.auth-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
.auth-link:hover {
  text-decoration: underline;
}

/* ============================================================
   半透明盒子的配套：控件自己补不透明底
   ============================================================
   盒子按要求做到 40% 透明，油菜花田的黄绿花纹会整片透过盒底。
   盒子的「留白区」透图是想要的，但**输入框绝不能跟着透** ——
   40% 透明度下叠上花纹，placeholder 和已输入的手机号都会花掉，
   验证码那 4 个字符更是直接认不出（它们本来就靠干扰线抗 OCR）。
   所以这里给输入控件单独补一层近不透明的底：
     盒子透图（装饰），控件不透明（可用）。
   别为了「整体统一」把输入框也调成半透明，那样没法用。

   另：EP 2.8 的输入底色变量是 --el-fill-color-blank，
   改动它等于一次性把 el-input / el-select / el-date-picker 全覆盖，
   比逐个类名更稳（el-select 在 2.8 里已换成 .el-select__wrapper，
   逐个类名要写两套，少写一套就漏）。 */
.auth-card {
  /* ★ 提到 0.985：0.96 时 4% 的透光足以让背后的山影/云影在框内显形
     （放大截图里能看到完整山脉轮廓压在文字底下）。输入框底的第一职责是
     「字必须清楚」，不是「透一点更透气」—— 盒子透图那部分已经在留白区了。
     0.985 在屏幕上与纯白不可分辨，但把 4% 的透光压到 1.5%。 */
  --el-fill-color-blank: rgba(255, 255, 255, 0.985);
  /* 表单正文色压深一档：EP 默认 #606266 在花田背景上偏灰 */
  --el-text-color-regular: #1b2f28;
  /* ★ 别指望这里改 --el-font-size-base 能放大输入框的字：
     EP 的 el-input[size=large] 会在组件自己的作用域里把这个变量
     重新设成 14px，把这里的值盖掉（实测设 16px 无效、computed 仍是 14px）。
     所以字号必须写在 .el-input__inner 上，见下方规则。 */
}
/* 输入框内的文字：EP size="large" 是 14px，在 45% 宽的大盒子里偏小，
   半透明底还会再吃掉一点笔画。直接写在这两个元素上，不走变量。
   ★ el-select 要写 **placeholder 与 selected-item 两个类名**：只写 selected-item
   时未选中状态的「请选择注册身份」仍是 14px。
   ★ 探针验字号要看**真正渲染文字的那个元素**（`.el-select__placeholder`，
     实测 16px），不是外层 `.el-select` / `.el-select__wrapper`（它们恒为 14px，
     读错会误判成「字号没生效」）。 */
.auth-card .el-input__inner,
.auth-card .el-select__selected-item,
.auth-card .el-select__placeholder {
  font-size: 16px;
  /* 16px 字配 40px 高的框会上下顶边。抬到 46px 才透气 ——
     EP 的 --el-component-size-large 是 40px，是给 14px 字准备的 */
  line-height: 1.5;
}
.auth-card .el-input__wrapper,
.auth-card .el-select__wrapper {
  /* ★ 两个都要写：el-select 的默认高度只有 46px，与输入框的 50px 不齐，
     同一行里一个高一截。min-height 对它无效（它有 height），
     所以额外显式给 height。 */
  min-height: 46px;
  align-items: center;
}
.auth-card .el-select__wrapper {
  height: 50px;
}

/* ★ label 与输入框贴得太近：label-position="top" 时 EP 的 label
   margin-bottom 很小，字号一放大就挤成「手机号」紧贴框上沿。
   label 15px 需要 6px 下间距才读得开 */
.auth-card .el-form-item__label {
  padding-bottom: 6px;
  line-height: 1.3;
}
/* 表单项之间也要放：16px 字 + 46px 框，18px 默认间距偏挤 */
.auth-card .el-form-item {
  margin-bottom: 20px;
}
/* 提交项的额外下间距由 .auth-switch 接手，这里去掉避免两段间距叠加 */
.auth-card .auth-submit {
  margin-bottom: 0;
}
/* ============================================================
   输入框外观：Material Design 官方 Outlined 规范
   ============================================================
   改之前的样子是 EP 默认的「4px 圆角 + 2px inset 阴影」，三个问题：
   ① 圆角只有 4px —— 48px 高的框配 4px 圆几乎看不出圆，整体是方盒子；
   ② 用 **inset** 画边框 —— 往里收的是凹槽，控件看着像刻进底图里，
      而不是一个「浮在底上的东西」；
   ③ 边框永远 2px 不变 —— 没有状态差，聚焦时用户看不出自己点的是哪个框。

   按 Material Outlined 的做法改（容器 56px / 边框 1px→聚焦 2px /
   圆角 / 边框色承载状态）：
     · 边框改用真实的 `border`，不用 inset 阴影
     · 默认 1px 中灰、聚焦 2px 主题色 —— 两档差异正好传达状态
     · 圆角 4px → 12px
     · 补一层很淡的外投影，让控件从半透明盒子上「浮起来」
   盒子只有 40% 透明 + blur，控件不投影的话会跟底图糊在一起。 */
.auth-card .el-input__wrapper,
.auth-card .el-select__wrapper,
.auth-card .el-textarea__inner {
  /* ★ 必须用 `border` 而不是 `box-shadow: … inset`。
     EP 的 wrapper 默认没有 border（border: 0），所以这里直接给全边框；
     用 box-shadow 模拟就会得到「凹槽感」，这是改版前最大的违和点 */
  border: 1px solid rgba(28, 68, 56, 0.28);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(12, 40, 32, 0.06);
  transition: border-color 0.2s cubic-bezier(0.645, 0.045, 0.355, 1),
    box-shadow 0.2s cubic-bezier(0.645, 0.045, 0.355, 1);
}
/* 聚焦：换成主题色 + 加粗到 2px。
   ★ **加粗用 `outline`，不要把 border 从 1px 改成 2px**：
   border 在盒模型里占位，改粗会让框「长大」1px、把下方内容顶下去跳一格；
   outline 不参与布局，聚焦加粗不会引起任何位移。 */
.auth-card .el-input__wrapper.is-focus,
.auth-card .el-select__wrapper.is-focused {
  border-color: var(--el-color-primary);
  /* outline 不参与布局，聚焦加粗时不会让下方内容跳一格 */
  outline: 2px solid rgba(14, 122, 95, 0.18);
  outline-offset: -1px;
  box-shadow: 0 2px 8px rgba(14, 122, 95, 0.16);
}
/* hover：边框略深，给出「这个可以点」的暗示（Material 的 Hover 状态）。
   ★ **必须排在 :focus 之后**，否则鼠标点进去时 hover 和 focus 同时生效、
     同优先级下后写的 hover 会盖掉 focus 的主题色边框 ——
     实测点击后 border 停在 rgba(28,68,56,0.45)（hover 色）而不是主题色。
     这里额外用 :not(:focus) 排除聚焦态，两道保险 */
.auth-card .el-input__wrapper:hover:not(.is-focus),
.auth-card .el-select__wrapper:hover:not(.is-focused) {
  border-color: rgba(28, 68, 56, 0.45);
}
.auth-card .el-form-item__label {
  /* ★ label 15px + 加重：EP 默认 14px/常规，在这套底上偏小偏淡 */
  font-size: 15px;
  font-weight: 600;
  color: #1b2f28;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.85);
}
/* ★ 去掉必填的红色星号（用户要求）。
   ★ 用 :before 的 content 覆盖**而不是**删 `.el-form-item.is-required`
   —— 那个 class 带着校验语义，删它可能连带影响 EP 的必填判定样式。
   content 置空是最小侵入的做法。 */
.auth-card .el-form-item.is-required:not(.is-no-asterisk) > .el-form-item__label::before {
  content: '' !important;
}
/* placeholder 在花田背景上最容易糊，压深一档 */
.auth-card .el-input__inner::placeholder,
.auth-card .el-select__placeholder.is-transparent {
  color: #7d918a;
}
/* 校验失败的报错是红字，叠花纹最容易糊，加一层白底衬 */
.auth-card .el-form-item__error {
  background: rgba(255, 255, 255, 0.78);
  padding: 0 4px;
  border-radius: 3px;
  width: fit-content;
}

/* 标题/副标题/切换行：暗字在半透明底上给一点白影提亮 */
.auth-title,
.auth-sub,
.auth-switch {
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.85);
}
</style>
