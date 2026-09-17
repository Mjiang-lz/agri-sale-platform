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
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  border-right: 1px solid var(--el-border-color-light);
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
</style>
