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
            <!-- 求购需求：**所有人都可见**，包括管理员。
                 管理员的职责包含「商品货源与求购需求审核」，需要看到这些数据；
                 该页对管理员是纯只读（不显示发布按钮与右侧「我发布的」）。
                 未登录时隐藏：进去要 401，不如不显示。 -->
            <el-menu-item v-if="isLoggedIn" index="/trade/demand">
              <RxtIcon name="ico-demand" />
              <span>求购需求</span>
            </el-menu-item>
            <!-- 购物车与我的订单属于买家侧动作，管理员一律隐藏：
                 加购与下单会写入真实订单数据，平台管理员账号不该产生这种记录。 -->
            <el-menu-item v-if="isLoggedIn && !isAdmin" index="/trade/cart">
              <RxtIcon name="ico-cart" />
              <span>购物车</span>
            </el-menu-item>
            <el-menu-item v-if="isLoggedIn && !isAdmin" index="/trade/orders">
              <RxtIcon name="ico-order" />
              <span>我的订单</span>
            </el-menu-item>
            <!-- 我的商品：仅农户可见。
                 ⚠️ 管理员**不显示**这一栏：他不参与发布商品，「我的商品」对自己是空的
                 （管理员名下没有任何商品），点进去只会看到一个红色报错 ——
                 后端 pageMyProducts 是 requireRole(FARMER)，管理员调它直接 403。
                 治理要看全平台在售货源，那是「商品货源」那一项，不在这里。 -->
            <el-menu-item v-if="isFarmer" index="/trade/my-products">
              <RxtIcon name="ico-source" />
              <span>我的商品</span>
            </el-menu-item>
          </el-sub-menu>
          <!-- 融资服务：这个菜单名下其实是三个不同的模块，按角色分流。
               · 农户    → 提交申请、看自己的进度（/finance/apply、/finance/my）
               · 银行用户 → 审批别人提交的申请（/finance/bank-review）
               · 管理员  → 与农户相同的展示页，但**只读**：后端 submitApply / getMyApply
               都是 requireRole(FARMER)，管理员真去提交会 403。所以这里给的是同一批页面，
               提交按钮由 FinanceApply.vue 里的 isReadOnly 置灰，另加一个看全部的融资台账。
               ⚠️ 三个分支必须覆盖全部角色：原先只有 v-if / v-else-if，
               管理员两个都不匹配，展开就是一个空下拉框（点得开但什么都没有）。 -->
          <el-sub-menu v-if="canSeeFinance" index="finance">
            <template #title>
              <RxtIcon name="ico-finance" />
              <span>融资服务</span>
            </template>
            <template v-if="isFarmerLike">
              <el-menu-item index="/finance/apply">
                <RxtIcon name="ico-apply" />
                <span>融资申请</span>
              </el-menu-item>
              <el-menu-item index="/finance/my">
                <RxtIcon name="ico-ledger" />
                <span>我的融资</span>
              </el-menu-item>
              <!-- 管理员专属：全平台台账。农户看不到别人的申请，这个入口只给管理员。 -->
              <el-menu-item v-if="isAdmin" index="/finance/ledger">
                <RxtIcon name="ico-ledger" />
                <span>融资台账</span>
              </el-menu-item>
            </template>
            <el-menu-item v-else-if="isBank" index="/finance/bank-review">
              <RxtIcon name="ico-ledger" />
              <span>融资审批</span>
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
          <el-sub-menu v-if="canSeeUser" index="user">
            <template #title>
              <RxtIcon name="ico-user" />
              <span>个人中心</span>
            </template>
            <el-menu-item index="/user/profile">
              <RxtIcon name="ico-user" />
              <span>个人信息</span>
            </el-menu-item>
            <el-menu-item index="/user/address">
              <RxtIcon name="ico-order" />
              <span>收货地址</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>

        <el-dropdown v-if="isLoggedIn" trigger="click" @command="handleUserCommand">
          <button class="user-button" type="button">
            <el-avatar :size="34" :src="avatarUrl">{{ nameInitial }}</el-avatar>
            <span class="ub-name">{{ displayName }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>账号身份：{{ actualRole || '未同步' }}</el-dropdown-item>
              
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

// 顶栏显示的是「昵称」，不是「身份」。
// 昵称 = realName —— 系统没有独立的 nickname 列，个人中心的表单
// 把这个字段标注为「昵称」（Profile.vue 的「昵称」表单项），两者是同一个值。
// 原来顶栏放的是 activeRole 首字 + 「当前身份：农户」，把「角色」当成了「用户名」。
const displayName = computed(() => userStore.profile?.realName || '用户')
const nameInitial = computed(() => displayName.value.slice(0, 1))
/**
 * 顶栏头像地址。
 *
 * ★ 这里必须写 `|| undefined` 而不是 `|| ''` —— el-avatar 的 :src 拿到空串时，
 *   浏览器会把它当成相对地址去请求**当前页面 URL**，白白多打一次请求。
 *
 * 为什么原来漏了：顶栏和昵称一起改的时候，头像只写了插槽（首字），
 * 没接 userStore.profile.avatarUrl。于是个人中心换了头像，
 * 个人中心那块（Profile.vue 的 el-avatar :src）会变，**顶栏纹丝不动**，
 * 用户会以为「上传没生效」。
 *
 * 死链不用额外处理：el-avatar 在图片 error 时会自动回退到插槽内容（昵称首字），
 * 不会显示碎图。库里那几条指向不存在文件的种子 avatar_url 就走这条回退。
 */
const avatarUrl = computed(() => userStore.profile?.avatarUrl || undefined)
const menuPerms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))
const isLoggedIn = computed(() => Boolean(userStore.token))

// 按「登录账号的真实角色」分流菜单：真实角色决定接口能不能调通，
// activeRole 只是前端的展示态（农户可切成采购方预览），拿它做权限分流会出现
// 「菜单没了但接口还能调」这种两边对不上的状态。
const isFarmer = computed(() => actualRole.value === '农户')
const isBank = computed(() => actualRole.value === '银行用户')

/**
 * 管理员与农户同权限的展示口径。
 *
 * 为什么管理员要和农户看到同一批页面：
 * 农户是这个平台的主体（供给侧 + 融资需求侧都归他），功能最多。
 * 管理员登录后若菜单比农户还少，既不符合实际使用场景，
 * 也会露出「按角色 v-if 没覆盖全」的空子菜单（见融资服务那段注释）。
 *
 * ⚠️ 这里是**展示**口径，不是接口权限口径。submitApply / getMyApply
 * 后端仍是 requireRole(FARMER)，所以管理员进入这些页面后提交按钮必须置灰，
 * 由 FinanceApply.vue 的 isReadOnly 负责 —— 前端放开显示 + 后端拦住写入，
 * 这样既不会空菜单，也不会让管理员真的提交出数据。
 */
const isAdmin = computed(() => actualRole.value === '管理员')
const isFarmerLike = computed(() => isFarmer.value || isAdmin.value)

// 未登录时也展示「农产品交易」入口：商品货源是公开页面，
// 若沿用后端菜单权限（游客为空）判断，访客在详情页会看到一个空导航。
//
// 管理员同样为 true：t_role 里管理员的 menu_perms 是
// `dashboard,audit,user,finance`，**不含 trade**，靠 includes 判断会把整个
// 农产品交易菜单藏起来 —— 而管理员恰恰需要看全平台在售货源来核对治理数据。
// 这里刻意不改数据库，前端放宽一个角色。
// 代价是这与后端 menu_perms 口径不一致（管理员的权限串里并没有 trade），
// 属于「展示口径」而非「权限口径」：写操作仍由各页面的只读态与后端拦截把关。
const canSeeTrade = computed(() => isLoggedIn.value ? (menuPerms.value.includes('trade') || isAdmin.value) : true)
const canSeeFinance = computed(() => menuPerms.value.includes('finance'))
const canSeeExpert = computed(() => menuPerms.value.includes('expert'))
const canSeeUser = computed(() => menuPerms.value.includes('user'))
const canSwitchRole = computed(() => ['农户', '采购方'].includes(actualRole.value))
const canAccessAppointment = computed(() => ['农户', '专家'].includes(actualRole.value))

const activeMenu = computed(() => {
  if (route.path.startsWith('/trade/product')) return '/trade/products'
  // 结算页在导航里没有对应条目，高亮回购物车 —— 否则从购物车点进结算后
  // 顶部会一个高亮都没有，看起来像脱离了导航
  if (route.path.startsWith('/trade/checkout')) return '/trade/cart'
  if (route.path.startsWith('/expert/')) return route.path
  if (route.path.startsWith('/finance/')) return route.path
  if (route.path.startsWith('/user/')) return route.path
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
.business-layout { min-height: 100vh; display: flex; flex-direction: column; background: var(--rxt-page-bg); }
/* 粘性头部：半透明 + 毛玻璃，滚动时内容从底下透出来。
   注意 backdrop-filter 在无头 Chrome（SwiftShader 软渲染）下会让合成器卡死、
   截图直接超时 —— 那是渲染环境的问题，不是这里的写法有问题，别据此删掉它。 */
.business-header {
  position: sticky; top: 0; z-index: 30;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--rxt-border);
  box-shadow: var(--rxt-shadow-sm);
  backdrop-filter: blur(14px);
}
.header-inner { width: var(--rxt-container); height: var(--rxt-header-h); margin: 0 auto; display: flex; align-items: center; gap: 26px; }
.brand { display: flex; align-items: center; gap: 10px; padding: 0; border: 0; background: transparent; color: var(--rxt-ink); cursor: pointer; white-space: nowrap; }
.brand strong, .brand small { display: block; }
.brand strong { font-size: 19px; letter-spacing: 1px; }
.brand small { margin-top: 2px; color: var(--el-text-color-secondary); font-size: 10px; }
.top-nav { flex: 1; border-bottom: 0 !important; }
.top-nav .el-menu-item, .top-nav :deep(.el-sub-menu__title) { font-weight: 600; }
/* 菜单图标与文字间距。用 :deep() 是因为 RxtIcon 是子组件，
   它的根元素带的是自己那份 scope id，父组件的普通选择器选不稳。 */
.top-nav :deep(.rxt-icon) { margin-right: 7px; }
.user-button { display: flex; align-items: center; gap: 8px; padding: 4px; border: 0; background: transparent; color: var(--el-text-color-primary); cursor: pointer; white-space: nowrap; }
/* 顶栏右侧显示昵称。比其他文字加粗一档 —— 原来这里是「当前身份：农户」这类
   说明性文字，现在它是账号主体本身 */
.ub-name { font-weight: 600; }
.guest-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.business-main { flex: 1; width: var(--rxt-container); margin: 0 auto; padding: 28px 0 46px; }
.business-footer { width: var(--rxt-container); margin: 0 auto; padding: 18px 0 26px; display: flex; justify-content: space-between; color: var(--rxt-ink-weak); border-top: 1px solid var(--rxt-border); font-size: 12px; }
@media (max-width: 1050px) { .header-inner { height: auto; padding: 12px 0; flex-wrap: wrap; gap: 10px; } .top-nav { order: 3; flex-basis: 100%; overflow-x: auto; } .user-button span { display: none; } }
@media (max-width: 680px) { .header-inner, .business-main, .business-footer { width: min(100% - 24px, 1420px); } .brand small { display: none; } .business-footer { flex-direction: column; gap: 6px; } }
</style>
