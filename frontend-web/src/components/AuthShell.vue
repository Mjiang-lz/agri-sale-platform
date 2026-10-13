<template>
  <!--
    登录 / 注册共用的页面外壳：整屏底图 + 右侧抽屉式表单盒。

    为什么抽出来：登录页与注册页除了表单内容之外完全同构（底图、抽屉尺寸、
    圆角、阴影、品牌标、页脚）。各自写一份的话，这两页的视觉一定会慢慢漂开
    —— 全站 .page-head 被复制 9 份就是这个剧本。

    版式（2026-10-13 按要求调整）：底图铺满整屏，表单盒不再居中，
    改为**贴右侧的抽屉**，宽 40%、盒体半透明 40%。
    这样左侧留出完整的油菜花田与人物，视觉重心在图上，表单只占右侧一条。

    ★ 版式又调过一版（"图标放头部、别分开、右侧铺满"）：
    之前标识在左下角、页脚在右下角，各自垫一块小牌子 —— 结果是
    「一条右侧面板上散着两个孤立方块」，盒子感被切碎了。
    现在改成**右侧一整块通栏盒子**：标识在盒内头部、表单占中间、
    页脚沉底，40% 宽从上到下铺满整屏。左侧只留纯底图。

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

/* ★ 右侧通栏盒子：品牌标识在头部、表单在中间、页脚沉底。
   半透明底与 backdrop-filter 都放在这里 —— 盒子只有这一块，
   标识/表单/页脚三段共用同一层底，所以它们天然是「一体的」。

   通栏的关键在 min-height:100vh + 三段用 flex 分配：
   头部与页脚 flex:0 0 auto 贴着上下边，中间 .auth-card 撑开剩余高度
   并自己居中，于是盒子在任何视口高度下都是满的。 */
.auth-drawer {
  position: relative;
  z-index: 1;
  width: 40%;
  min-width: 420px;
  max-width: 640px;
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
  font-size: 21px;
  letter-spacing: 1px;
  color: var(--rxt-ink);
}
.auth-brand-text small {
  margin-top: 3px;
  font-size: 11px;
  color: var(--rxt-ink-weak);
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
  font-size: 12px;
  color: #2c4038;
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
