<template>
  <div class="business-layout">
    <!-- 业务端布局：农户、采购方、专家等用户使用顶部导航，不使用侧边栏。 -->
    <header class="business-header">
      <div class="header-inner">
        <button class="brand" type="button" @click="router.push('/home')">
          <span class="brand-mark">融</span>
          <span><strong>融销通</strong><small>农产品融销一体平台</small></span>
        </button>

        <el-menu class="top-nav" mode="horizontal" :default-active="activeMenu" :ellipsis="false" router>
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

        <el-dropdown trigger="click" @command="handleUserCommand">
          <button class="user-button" type="button"><el-avatar :size="34">农</el-avatar><span>融销通用户</span></button>
          <template #dropdown>
            <el-dropdown-menu><el-dropdown-item command="profile">个人中心</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="business-main"><router-view /></main>
    <footer class="business-footer"><span>融销通 · 农产品融销一体平台</span><span>{{ route.meta.title || '业务端' }}</span></footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/trade/product')) return '/trade/products'
  if (route.path.startsWith('/expert/')) return route.path
  if (route.path.startsWith('/finance/')) return route.path
  return route.path
})

function handleUserCommand(command) {
  if (command === 'profile') {
    router.push('/user/profile')
    return
  }
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.business-layout { min-height: 100vh; display: flex; flex-direction: column; background: #f4f7f4; }
.business-header { position: sticky; top: 0; z-index: 30; background: rgba(255,255,255,.96); border-bottom: 1px solid var(--el-border-color-light); backdrop-filter: blur(14px); }
.header-inner { width: min(1420px, calc(100% - 36px)); height: 70px; margin: 0 auto; display: flex; align-items: center; gap: 26px; }
.brand { display: flex; align-items: center; gap: 10px; padding: 0; border: 0; background: transparent; color: #194a30; cursor: pointer; white-space: nowrap; }
.brand-mark { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; color: #fff; background: linear-gradient(145deg, #25734b, #4aa16d); font-weight: 700; box-shadow: 0 8px 18px rgba(37,115,75,.2); }
.brand strong, .brand small { display: block; }
.brand strong { font-size: 19px; letter-spacing: 1px; }
.brand small { margin-top: 2px; color: var(--el-text-color-secondary); font-size: 10px; }
.top-nav { flex: 1; border-bottom: 0 !important; }
.top-nav .el-menu-item, .top-nav :deep(.el-sub-menu__title) { font-weight: 600; }
.user-button { display: flex; align-items: center; gap: 8px; padding: 4px; border: 0; background: transparent; color: var(--el-text-color-primary); cursor: pointer; white-space: nowrap; }
.business-main { flex: 1; width: min(1420px, calc(100% - 36px)); margin: 0 auto; padding: 28px 0 46px; }
.business-footer { width: min(1420px, calc(100% - 36px)); margin: 0 auto; padding: 18px 0 26px; display: flex; justify-content: space-between; color: #87938b; border-top: 1px solid var(--el-border-color-light); font-size: 12px; }
@media (max-width: 1050px) { .header-inner { height: auto; padding: 12px 0; flex-wrap: wrap; gap: 10px; } .top-nav { order: 3; flex-basis: 100%; overflow-x: auto; } .user-button span { display: none; } }
@media (max-width: 680px) { .header-inner, .business-main, .business-footer { width: min(100% - 24px, 1420px); } .brand small { display: none; } .business-footer { flex-direction: column; gap: 6px; } }
</style>
