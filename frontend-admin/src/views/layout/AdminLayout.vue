<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">
        <RxtLogo :size="28" />
        <div class="brand-text">
          <strong>融销通</strong>
          <small>管理后台</small>
        </div>
      </div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/dashboard">
          <RxtIcon name="ico-dashboard" />
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/user/list">
          <RxtIcon name="ico-user" />
          <span>用户管理</span>
        </el-menu-item>
        <el-sub-menu index="audit">
          <template #title>
            <RxtIcon name="ico-audit" />
            <span>内容审核</span>
          </template>
          <el-menu-item index="/audit/product">
            <RxtIcon name="ico-source" />
            <span>商品审核</span>
          </el-menu-item>
          <el-menu-item index="/audit/knowledge">
            <RxtIcon name="ico-knowledge" />
            <span>知识审核</span>
          </el-menu-item>
          <el-menu-item index="/audit/comment">
            <RxtIcon name="ico-qa" />
            <span>评论审核</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/finance/manage">
          <RxtIcon name="ico-finance" />
          <span>融资管理</span>
        </el-menu-item>
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
import RxtIcon from '@/components/RxtIcon.vue'
import RxtLogo from '@/components/RxtLogo.vue'
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
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: #14302B;
  border-bottom: 1px solid var(--el-border-color-light);
  overflow: hidden;
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.brand-text strong {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.brand-text small {
  margin-top: 2px;
  font-size: 10px;
  letter-spacing: .5px;
  color: var(--el-text-color-secondary);
}
/* 图标与文字间距。:deep() 是因为 RxtIcon 是子组件，
   其根元素带的是它自己的 scope id，父组件普通选择器选不稳。 */
.aside :deep(.rxt-icon) {
  margin-right: 8px;
}
.header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}
</style>
