<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">融销通</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/home">首页</el-menu-item>
        <el-sub-menu index="trade">
          <template #title>农产品交易</template>
          <el-menu-item index="/trade/products">商品货源</el-menu-item>
          <el-menu-item index="/trade/demand">求购需求</el-menu-item>
          <el-menu-item index="/trade/cart">购物车</el-menu-item>
          <el-menu-item index="/trade/orders">我的订单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="finance">
          <template #title>融资服务</template>
          <el-menu-item index="/finance/apply">融资申请</el-menu-item>
          <el-menu-item index="/finance/my">我的融资</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="expert">
          <template #title>专家助力</template>
          <el-menu-item index="/expert/knowledge">农业知识</el-menu-item>
          <el-menu-item index="/expert/qa">专家问答</el-menu-item>
          <el-menu-item index="/expert/appointment">专家预约</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/user/profile">个人中心</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title || '融销通' }}</span>
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
