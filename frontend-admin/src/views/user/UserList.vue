<template>
  <el-card shadow="never">
    <template #header>用户管理</template>
    <el-empty description="页面待开发，接口位置见下方说明" />
    <p class="hint">对应后端接口：GET /api/admin/users（启停用 PUT /api/admin/users/{userId}/status，改角色 PUT /api/admin/users/{userId}/role，角色下拉 GET /api/admin/roles）</p>
  </el-card>
</template>

<script setup>
// TODO 待实现：按《软件需求规格说明书》对应功能点补齐页面交互
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
