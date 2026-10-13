<template>
  <!--
    登录 / 注册共用的页面外壳：整屏底图 + 右侧抽屉式表单盒。

    为什么抽出来：登录页与注册页除了表单内容之外完全同构（底图、抽屉尺寸、
    圆角、阴影、品牌标、页脚）。各自写一份的话，这两页的视觉一定会慢慢漂开
    —— 全站 .page-head 被复制 9 份就是这个剧本。

    版式（2026-10-13 按要求调整）：底图铺满整屏，表单盒不再居中，
    改为**贴右侧的抽屉**，宽 40%、盒体半透明 40%。
    这样左侧留出完整的油菜花田与人物，视觉重心在图上，表单只占右侧一条。

    ★ 半透明带来的代价：油菜花田是高饱和黄绿，40% 透明度下
    表单文字会糊在花纹上。所以**输入框自己补了一层近不透明底**，
    只有盒子的留白区透图 —— 这是「盒子透、控件不透」的分层处理，
    不要为了整体统一把输入框也调成半透明，那样没法用。

    用法：<AuthShell><h1 class="auth-title">…</h1>…表单…</AuthShell>
    标题/表单用下面导出的类名（.auth-title / .auth-sub / .auth-switch / .auth-link /
    .auth-submit），它们写在本组件的非作用域块里，所以插槽内容也能吃到。
  -->
  <div class="auth-page" :style="{ '--auth-field': fieldUrl }">
    <!-- 底图上的品牌标识：盒子腾给表单后，标识挪到左下角，兼顾「这是谁」 -->
    <div class="auth-brand">
      <RxtLogo :size="46" />
      <div class="auth-brand-text">
        <strong>融销通</strong>
        <small>农产品融销一体平台</small>
      </div>
    </div>

    <aside class="auth-drawer">
      <main class="auth-card">
        <slot />
      </main>

      <footer class="auth-foot">
        <router-link class="auth-link" to="/home">← 返回首页</router-link>
        <span>基于数字经济的农产品融销一体平台</span>
      </footer>
    </aside>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import RxtLogo from '@/components/RxtLogo.vue'

// ★ public/ 下的图必须跟 BASE_URL 走（web 端构建期是 /app/），写死 /auth/… 会 404
//
// ★ 这里必须在 JS 里就把 url() 包好，不能只把裸路径塞进 CSS 变量：
//   background-image 的图层语法不接受裸路径（只有 url() 或 gradient），
//   写成 `background: var(--auth-field) …` 整条声明会被**静默丢弃** ——
//   页面照常渲染、computed background-image 只是 none，看不出任何报错。
//   长写法 background-image/size/attachment 同样丢，别以为换长写法能绕开。
const fieldUrl = computed(() => `url("${import.meta.env.BASE_URL}auth/auth-field.jpg")`)
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

/* 品牌标识：放在左下角的白底牌子上。
   天空区是整张图最亮的一带，标识直接压上去会看不清 */
.auth-brand {
  position: absolute;
  left: 40px;
  bottom: 40px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 22px 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 30px rgba(20, 52, 42, 0.18);
}
.auth-brand-text strong,
.auth-brand-text small {
  display: block;
}
.auth-brand-text strong {
  font-size: 22px;
  letter-spacing: 1px;
  color: var(--rxt-ink);
}
.auth-brand-text small {
  margin-top: 3px;
  font-size: 11px;
  color: var(--rxt-ink-weak);
}

/* 右侧抽屉：占页面 40%，表单盒在半透明面板内 */
.auth-drawer {
  position: relative;
  z-index: 1;
  width: 40%;
  min-width: 420px;
  max-width: 640px;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* ★ safe 兜底：注册页五个字段在矮视口里会高过屏幕，纯 center 会把标题顶出可视区
     （flex 居中 + 溢出 ⇒ 首尾被裁）。safe center 在装得下时等价于 center，装不下时退化为 flex-start */
  justify-content: safe center;
  gap: 18px;
  padding: 48px 32px;
  box-sizing: border-box;
  overflow-y: auto;
  /* 抽屉自身再补一层极淡白，让右侧与左侧的图有个过渡，不至于硬切。
     ★ 刻意做得很薄（0.12 / 0.22）：它叠在盒子的 0.4 之上，
     早期版本给到 0.5，叠完等于 0.7 白 —— 盒子「40% 透明」的要求
     就被这层吃掉了，视觉上又回到一张不透明的卡片。白晕的活儿
     交给 .auth-card 里的 backdrop-filter 干，那才是给盒子该加的 */
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.12) 34%, rgba(255, 255, 255, 0.22) 100%);
}

.auth-card {
  position: relative;
  width: 100%;
  padding: 32px 30px 24px;
  box-sizing: border-box;
  /* ★ 盒子透明度 40%（需求指定）：白底只留四成，油菜花田透过来。
     配套必须有 backdrop-filter 模糊 —— 单靠 40% 透明度，
     花田的高频黄绿纹理会直接透过文字底下，字会花掉 */
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(14px) saturate(115%);
  -webkit-backdrop-filter: blur(14px) saturate(115%);
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(20, 52, 42, 0.16);
}

.auth-foot {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  /* ★ 页脚不在 .auth-card 里，它底下就是油菜花田 —— 只靠文字阴影压不住
     高饱和的黄绿花纹（截图里这行字基本认不出来）。所以自己垫一块浅底牌，
     跟左下角的 .auth-brand 同一套视觉语言：一个字号的白底牌子浮在图上。 */
  padding: 9px 20px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: 0 6px 18px rgba(20, 52, 42, 0.12);
  font-size: 12px;
  color: #2c4038;
}
.auth-foot .auth-link {
  font-weight: 600;
}

/* ★ 窄屏（平板竖屏 / 手机）：40% 抽屉加 min-width:420px 会溢出，
   一旦横向出现滚动条整页构图就废了 —— 直接让抽屉占满整宽，
   底图退成纯底色（不然糊在表单后面的花纹会跟着一起晃） */
@media (max-width: 900px) {
  .auth-page {
    /* 满宽时不再需要横向过渡带，也铺不了满屏底图 */
    background:
      linear-gradient(180deg, rgba(14, 122, 95, 0.08) 0%, rgba(14, 122, 95, 0.14) 100%),
      var(--rxt-page-bg);
  }
  .auth-drawer {
    width: 100%;
    min-width: 0;
    max-width: none;
    padding: 32px 20px;
    background: none;
  }
  /* 品牌标识挪到顶部：满宽时它压在左下角会跟表单的「返回首页」打架 */
  .auth-brand {
    position: static;
    align-self: center;
    margin-bottom: 4px;
  }
  .auth-page {
    flex-direction: column;
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
  font-size: 20px;
  font-weight: 600;
  color: var(--rxt-ink);
}
.auth-sub {
  margin: 8px 0 22px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
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
  font-weight: 600;
  letter-spacing: 2px;
}

.auth-switch {
  margin-top: 4px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
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
  --el-fill-color-blank: rgba(255, 255, 255, 0.93);
  /* 表单 label 与报错也在半透明底上，描一道淡影把字从花纹里拎出来 */
  --el-text-color-regular: #22332c;
}
.auth-card .el-input__wrapper,
.auth-card .el-select__wrapper,
.auth-card .el-textarea__inner {
  box-shadow: 0 0 0 1px rgba(20, 52, 42, 0.14) inset;
}
.auth-card .el-input__wrapper.is-focus,
.auth-card .el-select__wrapper.is-focused {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
.auth-card .el-form-item__label {
  color: #22332c;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.8);
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
