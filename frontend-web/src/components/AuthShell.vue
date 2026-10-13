<template>
  <!--
    登录 / 注册共用的页面外壳：田间航拍底图 + 居中卡片 + 品牌标 + 页脚。

    为什么抽出来：登录页与注册页除了表单内容之外完全同构（底图、卡片尺寸、
    圆角、阴影、品牌标、页脚）。各自写一份的话，这两页的视觉一定会慢慢漂开
    —— 全站 .page-head 被复制 9 份就是这个剧本。

    用法：<AuthShell><h1 class="auth-title">…</h1>…表单…</AuthShell>
    标题/表单用下面导出的类名（.auth-title / .auth-sub / .auth-switch / .auth-link /
    .auth-submit），它们写在本组件的非作用域块里，所以插槽内容也能吃到。
  -->
  <div class="auth-page" :style="{ '--auth-field': fieldUrl }">
    <main class="auth-card">
      <header class="auth-brand">
        <RxtLogo :size="46" />
        <div class="auth-brand-text">
          <strong>融销通</strong>
          <small>农产品融销一体平台</small>
        </div>
      </header>
      <slot />
    </main>

    <footer class="auth-foot">
      <router-link class="auth-link" to="/home">← 返回首页</router-link>
      <span>融销通 · 基于数字经济的农产品融销一体平台</span>
    </footer>
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  padding: 40px 16px;
  box-sizing: border-box;
  /* 田间航拍铺满整屏。
     罩层刻意做得**薄**（0.42→0.64）：厚了虽然可读性好，但纹理会糊成一片白，
     等于又回到「纯白渐变」。真正保证表单可读性的是卡片本身那层不透明白底，
     罩层只需压住梯田的高光与深色梯埂，别让它们跟标题抢注意力。

     顶部那一段「禾青」是给原图晨雾天空区补的：那是全图最亮最空的一带，
     不压一下会在页面顶部留出一条突兀的白边。 */
  background:
    linear-gradient(180deg, rgba(14, 122, 95, 0.16) 0%, rgba(14, 122, 95, 0.04) 22%, transparent 42%),
    radial-gradient(1100px 420px at 50% -160px, rgba(14, 122, 95, 0.14), transparent 70%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0.54) 46%, rgba(255, 255, 255, 0.64) 100%),
    var(--auth-field) center / cover no-repeat fixed;
  background-color: var(--rxt-page-bg);
}

/* 图上再压一层柔化，只护住卡片所在的中段，两侧留给纹理 */
.auth-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(680px 500px at 50% 46%, rgba(255, 255, 255, 0.5), transparent 74%);
  pointer-events: none;
}

.auth-card {
  position: relative;
  width: min(440px, 100%);
  padding: 34px 34px 26px;
  box-sizing: border-box;
  /* 罩层调薄后，卡片必须自己变实，否则表格会透出梯田纹理影响输入 */
  background: #ffffff;
  border: 1px solid var(--rxt-border);
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(20, 52, 42, 0.16);
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 12px;
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

.auth-foot {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  /* 页脚压在图上，字要比平时重一档才读得清 */
  font-size: 12px;
  color: #2c4038;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.85);
}
.auth-foot .auth-link {
  font-weight: 600;
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
</style>
