<template>
  <div class="business-layout">
    <!-- 业务端布局：业务角色使用顶部导航，不使用侧边栏。 -->
    <header class="business-header">
      <div class="header-inner">
        <button class="brand" type="button" @click="router.push('/home')">
          <RxtLogo :size="38" />
          <span><strong>融销通</strong><small>农产品融销一体平台</small></span>
        </button>

        <el-menu class="top-nav" mode="horizontal" :default-active="activeMenu" :ellipsis="false" router>
          <el-menu-item index="/home">
            <RxtIcon name="ico-home" />
            <span>首页</span>
          </el-menu-item>
          <el-sub-menu v-if="canSeeTrade" index="trade">
            <template #title>
              <RxtIcon name="ico-trade" />
              <span>农产品交易</span>
            </template>
            <el-menu-item index="/trade/products">
              <RxtIcon name="ico-source" />
              <span>商品货源</span>
            </el-menu-item>
            <!-- 下面三项都属于「我的数据」，未登录时进去只会被接口打回 401，
                 与其让人白点一次，不如先不显示 -->
            <el-menu-item v-if="isLoggedIn" index="/trade/demand">
              <RxtIcon name="ico-demand" />
              <span>求购需求</span>
            </el-menu-item>
            <el-menu-item v-if="isLoggedIn" index="/trade/cart">
              <RxtIcon name="ico-cart" />
              <span>购物车</span>
            </el-menu-item>
            <el-menu-item v-if="isLoggedIn" index="/trade/orders">
              <RxtIcon name="ico-order" />
              <span>我的订单</span>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu v-if="canSeeFinance && activeRole === '农户'" index="finance">
            <template #title>
              <RxtIcon name="ico-finance" />
              <span>融资服务</span>
            </template>
            <el-menu-item index="/finance/apply">
              <RxtIcon name="ico-apply" />
              <span>融资申请</span>
            </el-menu-item>
            <el-menu-item index="/finance/my">
              <RxtIcon name="ico-ledger" />
              <span>我的融资</span>
            </el-menu-item>
          </el-sub-menu>
          <template v-if="canSeeExpert && actualRole === '专家'">
            <el-menu-item index="/expert/knowledge">
              <RxtIcon name="ico-knowledge" />
              <span>农业知识</span>
            </el-menu-item>
            <el-menu-item index="/expert/qa">
              <RxtIcon name="ico-qa" />
              <span>专家问答</span>
            </el-menu-item>
            <el-menu-item v-if="canAccessAppointment" index="/expert/appointment">
              <RxtIcon name="ico-calendar" />
              <span>专家预约</span>
            </el-menu-item>
          </template>
          <el-sub-menu v-else-if="canSeeExpert" index="expert">
            <template #title>
              <RxtIcon name="ico-expert" />
              <span>专家助力</span>
            </template>
            <el-menu-item index="/expert/knowledge">
              <RxtIcon name="ico-knowledge" />
              <span>农业知识</span>
            </el-menu-item>
            <el-menu-item index="/expert/qa">
              <RxtIcon name="ico-qa" />
              <span>专家问答</span>
            </el-menu-item>
            <el-menu-item v-if="canAccessAppointment" index="/expert/appointment">
              <RxtIcon name="ico-calendar" />
              <span>专家预约</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-if="canSeeUser" index="/user/profile">
            <RxtIcon name="ico-user" />
            <span>个人中心</span>
          </el-menu-item>
        </el-menu>

        <el-dropdown v-if="isLoggedIn" trigger="click" @command="handleUserCommand">
          <button class="user-button" type="button">
            <el-avatar :size="34">{{ activeRole.slice(0, 1) }}</el-avatar>
            <span>当前身份：{{ activeRole }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>登录角色：{{ actualRole || '未同步' }}</el-dropdown-item>
              <el-dropdown-item v-if="canSwitchRole" command="role:农户" :disabled="activeRole === '农户'">切换为农户</el-dropdown-item>
              <el-dropdown-item v-if="canSwitchRole" command="role:采购方" :disabled="activeRole === '采购方'">切换为采购方</el-dropdown-item>
              <el-dropdown-item disabled>身份切换仅影响前端导航，接口权限仍按登录账号</el-dropdown-item>
              <el-dropdown-item divided command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 未登录访客：商品货源与商品详情已经开放，此时不能显示身份切换器 ——
             activeRole 在无登录态时会退回 '农户' 这个默认值，展示出来就是一句
             「当前身份：农户」的假信息，点开还会给出「退出登录」这种无处可退的菜单 -->
        <div v-else class="guest-actions">
          <el-button link @click="router.push('/login')">登录</el-button>
          <el-button type="primary" size="small" @click="router.push('/register')">注册</el-button>
        </div>
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
import RxtIcon from '@/components/RxtIcon.vue'
import RxtLogo from '@/components/RxtLogo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeRole = computed(() => userStore.activeRole)
const actualRole = computed(() => userStore.profile?.roleName || '')
const menuPerms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))
const isLoggedIn = computed(() => Boolean(userStore.token))
// 未登录时也展示「农产品交易」入口：商品货源是公开页面，
// 若沿用后端菜单权限（游客为空）判断，访客在详情页会看到一个空导航
const canSeeTrade = computed(() => isLoggedIn.value ? menuPerms.value.includes('trade') : true)
const canSeeFinance = computed(() => menuPerms.value.includes('finance'))
const canSeeExpert = computed(() => menuPerms.value.includes('expert'))
const canSeeUser = computed(() => menuPerms.value.includes('user'))
const canSwitchRole = computed(() => ['农户', '采购方'].includes(actualRole.value))
const canAccessAppointment = computed(() => ['农户', '专家'].includes(actualRole.value))

const activeMenu = computed(() => {
  if (route.path.startsWith('/trade/product')) return '/trade/products'
  if (route.path.startsWith('/expert/')) return route.path
  if (route.path.startsWith('/finance/')) return route.path
  return route.path
})

function handleUserCommand(command) {
  if (command.startsWith('role:')) {
    userStore.setActiveRole(command.slice(5))
    return
  }
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
.brand { display: flex; align-items: center; gap: 10px; padding: 0; border: 0; background: transparent; color: #14302B; cursor: pointer; white-space: nowrap; }
.brand strong, .brand small { display: block; }
.brand strong { font-size: 19px; letter-spacing: 1px; }
.brand small { margin-top: 2px; color: var(--el-text-color-secondary); font-size: 10px; }
.top-nav { flex: 1; border-bottom: 0 !important; }
.top-nav .el-menu-item, .top-nav :deep(.el-sub-menu__title) { font-weight: 600; }
/* 菜单图标与文字间距。用 :deep() 是因为 RxtIcon 是子组件，
   它的根元素带的是自己那份 scope id，父组件的普通选择器选不稳。 */
.top-nav :deep(.rxt-icon) { margin-right: 7px; }
.user-button { display: flex; align-items: center; gap: 8px; padding: 4px; border: 0; background: transparent; color: var(--el-text-color-primary); cursor: pointer; white-space: nowrap; }
.guest-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.business-main { flex: 1; width: min(1420px, calc(100% - 36px)); margin: 0 auto; padding: 28px 0 46px; }
.business-footer { width: min(1420px, calc(100% - 36px)); margin: 0 auto; padding: 18px 0 26px; display: flex; justify-content: space-between; color: #87938b; border-top: 1px solid var(--el-border-color-light); font-size: 12px; }
@media (max-width: 1050px) { .header-inner { height: auto; padding: 12px 0; flex-wrap: wrap; gap: 10px; } .top-nav { order: 3; flex-basis: 100%; overflow-x: auto; } .user-button span { display: none; } }
@media (max-width: 680px) { .header-inner, .business-main, .business-footer { width: min(100% - 24px, 1420px); } .brand small { display: none; } .business-footer { flex-direction: column; gap: 6px; } }
</style>
