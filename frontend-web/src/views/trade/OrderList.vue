<template>
  <el-card shadow="never">
    <template #header>我的订单</template>
    <el-empty description="页面待开发，接口位置见下方说明" />
    <!-- 注：原注释写的 /api/trade/orders 是早期约定，实际路径为 /api/trade/order/list（单数 order）。
         详情 GET /api/trade/order/{orderId}；支付、收货、取消见 docs/接口约定-M2订单流转.md -->
    <p class="hint">对应后端接口：GET /api/trade/order/list</p>
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
