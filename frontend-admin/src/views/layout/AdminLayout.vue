<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">融销通 · 管理后台</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/dashboard">仪表盘</el-menu-item>
        <el-menu-item index="/user/list">用户管理</el-menu-item>
        <el-sub-menu index="audit">
          <template #title>内容审核</template>
          <el-menu-item index="/audit/product">商品审核</el-menu-item>
          <el-menu-item index="/audit/knowledge">知识审核</el-menu-item>
          <el-menu-item index="/audit/comment">评论审核</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/finance/manage">融资管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title || '融销通 · 管理后台' }}</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
      <el-footer class="footer">
        <span class="footer-text">融销通 · 农产品产销融资管理平台</span>
        <span class="footer-text">Copyright © {{ year }} 融销通 · 管理后台</span>
      </el-footer>
    </el-container>
  </el-container>
</template>

<script setup>
const year = new Date().getFullYear()
</script>

<style scoped>
.layout {
  /* 不锁高度：让内容自然撑开，页脚跟在内容末尾、滚到底才出现 */
  min-height: 100vh;
}
.aside {
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}
.brand {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 2px;
}
.header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}
.main {
  /* 让滚动交给整个页面：el-main 默认 overflow:auto 会自己开一个滚动区，
     那样 footer 会被固定在框内。改成 visible 后页面整体滚动，页脚自然落在内容末尾 */
  overflow: visible;
  min-height: 0;
}
.footer {
  flex-shrink: 0;
  height: auto;
  padding: 12px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.footer-text + .footer-text::before {
  content: '|';
  margin-right: 12px;
  color: var(--el-border-color);
}
</style>
