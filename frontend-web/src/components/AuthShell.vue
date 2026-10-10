<template>
  <!--
    登录 / 注册共用的页面外壳：浅色品牌底纹 + 居中卡片 + 品牌标 + 页脚。

    为什么抽出来：登录页与注册页除了表单内容之外完全同构（底纹、卡片尺寸、
    圆角、阴影、品牌标、页脚）。各自写一份的话，这两页的视觉一定会慢慢漂开
    —— 全站 .page-head 被复制 9 份就是这个剧本。

    用法：<AuthShell><h1 class="auth-title">…</h1>…表单…</AuthShell>
    标题/表单用下面导出的类名（.auth-title / .auth-sub / .auth-switch / .auth-link /
    .auth-submit），它们写在本组件的非作用域块里，所以插槽内容也能吃到。
  -->
  <div class="auth-page">
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
import RxtLogo from '@/components/RxtLogo.vue'
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  padding: 40px 16px;
  box-sizing: border-box;
  /* 浅色品牌底纹：顶部一层禾青薄雾向下过渡到页面底色。
     比原来的 --el-fill-color-light 纯灰更有身份感，又不像深色那样压表单可读性 */
  background:
    radial-gradient(1100px 420px at 50% -160px, rgba(14, 122, 95, 0.1), transparent 70%),
    linear-gradient(180deg, #eef5f2 0%, var(--rxt-page-bg) 46%, #ffffff 100%);
}

.auth-card {
  width: min(440px, 100%);
  padding: 34px 34px 26px;
  box-sizing: border-box;
  background: var(--rxt-surface);
  border: 1px solid var(--rxt-border);
  border-radius: 18px;
  box-shadow: var(--rxt-shadow-lg);
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
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--rxt-ink-weak);
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
