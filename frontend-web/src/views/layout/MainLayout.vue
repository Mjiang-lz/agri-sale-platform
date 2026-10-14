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
              <RxtIcon name="ico-myproduct" />
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
          <!--
            专家角色且**尚未通过资格认证**：不给专家工作台，只留一条去认证的入口。
            未过审的专家看不到待处理提问/预约，是因为「能处理别人的提问」本身就是
            一种执业资格，认证通过才该给（后端 /expert/qa/pending 等接口也会 403，
            这里只是不让人看见一个点了就报错的入口）。
          -->
          <template v-if="isPendingExpert">
            <el-menu-item index="/user/profile">
              <RxtIcon name="ico-expert" />
              <span>专家资格认证</span>
            </el-menu-item>
          </template>
          <template v-else-if="canSeeExpert && actualRole === '专家'">
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
              <RxtIcon name="ico-address" />
              <span>收货地址</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>

        <!-- 已登录：头像 + 消息提醒 + 退出登录（自左向右）。
             2026-10-13 第一次改版，原先是 el-dropdown（账号身份 / 个人中心 / 退出登录）：
               · 「账号身份」是一行 disabled 文字，信息与顶栏按角色的菜单分流重复；
               · 退出登录藏在两层菜单最里面，用户明确反馈「这个没必要要了」。
             ⇒ 退出提成常驻按钮；头像本身即「进个人中心」的入口，点一下直达。
             2026-10-13 第二次改版（用户对着截图提的两条）：
               · 退出登录**只留图标**，去掉文字；
               · 顺序改为「铃铛在左、退出在最右」，即 铃铛 → 头像 → 退出。
             2026-10-14 第三次改版（用户对着截图提的第 1 条）：
               · 顺序再调，头像提到最左：头像 → 铃铛 → 退出。
               · 理由：「我是谁」应当先于「有什么通知」—— 本平台多角色切换频繁，
                 扫顶栏时第一眼要能确认自己正以哪个账号在看。
             ★ 退出去掉文字后，语义就只能靠 title / aria-label 承载 —— 缺了它们，
               这个按钮对视障用户、以及「悬停想确认一下」的用户都是无字的。
               两个图标按钮统一成 34×34 圆角方块，视觉上算一组（见 .logout-btn）。 -->
        <div v-if="isLoggedIn" class="user-area">
          <button class="user-button" type="button" title="进入个人中心" @click="router.push('/user/profile')">
            <el-avatar :size="34" :src="avatarUrl">{{ nameInitial }}</el-avatar>
            <span class="ub-name">{{ displayName }}</span>
          </button>

          <el-popover
            v-model:visible="msgOpen"
            placement="bottom-end"
            :width="372"
            trigger="click"
            popper-class="msg-popover"
            @show="loadMessages(true)"
          >
            <template #reference>
              <el-badge :value="msgTotal" :hidden="msgTotal === 0" :max="99" class="msg-badge">
                <button class="icon-btn" type="button" aria-label="消息提醒">
                  <RxtIcon name="ico-bell" :size="20" />
                </button>
              </el-badge>
            </template>

            <!-- v-loading：EP 全量注册时该指令是全局的（main.js 走 app.use(ElementPlus)） -->
            <div class="msg-panel" v-loading="msgLoading">
              <header class="msg-head">
                <strong>消息提醒</strong>
                <!-- ★ 标题报「待办总数」（不含已读概念），角标才报未读。
                     清红点不能让「还有几件事要做」一起消失 -->
                <span>{{ msgPendingTotal ? `${msgPendingTotal} 项待处理` : '暂无待处理' }}</span>
              </header>
              <ul v-if="msgItems.length" class="msg-list">
                <li
                  v-for="m in msgItems"
                  :key="m.key"
                  class="msg-item"
                  :class="{ 'is-read': !unreadOf(m) }"
                  @click="goMessage(m)"
                >
                  <span class="msg-dot" :class="`is-${m.tone}`"></span>
                  <div class="msg-body">
                    <strong>{{ m.title }}<em v-if="m.count > 1">×{{ m.count }}</em></strong>
                    <small>{{ m.desc }}</small>
                  </div>
                  <span class="msg-go">→</span>
                </li>
              </ul>
              <p v-else class="msg-empty">当前没有需要你处理的事项</p>
              <footer class="msg-foot">
                <el-button
                  link
                  type="primary"
                  :disabled="!msgTotal"
                  title="把当前待办标记为已看，消除铃铛上的红点"
                  @click="markAllRead"
                >
                  全部已读
                </el-button>
                <el-button link type="primary" :loading="msgLoading" @click="loadMessages(true)">刷新</el-button>
              </footer>
            </div>
          </el-popover>

          <!-- 退出登录：整行最右端，只留图标（用户 2026-10-13 要求去掉文字）。
               不写 size="small" —— 尺寸由 .logout-btn 定死成 34×34，与铃铛一致。 -->
          <el-button class="logout-btn" plain title="退出登录" aria-label="退出登录" @click="handleLogout">
            <RxtIcon name="ico-logout" :size="17" />
          </el-button>
        </div>

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
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useMessages } from '@/composables/useMessages'
import { getExpertProfile } from '@/api/expert'
import RxtIcon from '@/components/RxtIcon.vue'
import RxtLogo from '@/components/RxtLogo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
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
const canAccessAppointment = computed(() => ['农户', '专家'].includes(actualRole.value))

/* ---------- 专家资格认证闸门（需求 10） ---------- */
const expertAuditStatus = ref('')
/**
 * 是否是「还没通过资格认证的专家」。
 *
 * ★ 只有真实角色是专家、且档案状态不是「已通过」时才为 true。
 *   三种情形分别为 false：
 *     · 非专家 —— 与认证无关；
 *     · 档案拉取失败（游客、非专家、网络抖动）—— 不能因为一次拉取失败
 *       就把专家的整个工作台藏起来，那比多显示一个入口糟糕得多；
 *     · 已通过 —— 正常显示工作台。
 *
 * 初值为空串而不是 true/false：认证状态是**登录后异步拉**的，
 * 初值若写成 false，用户刷新瞬间会看到完整菜单闪一下再消失。
 * 空串在模板里与 false 同义，但语义是「还不知道」，不会误判。
 */
const isPendingExpert = computed(() => {
  if (actualRole.value !== '专家') return false
  if (!expertAuditStatus.value) return false
  return expertAuditStatus.value !== '已通过'
})

if (actualRole.value === '专家') {
  // 只有专家角色才拉：非专家调这个接口后端一律 403，没必要发一次注定失败的请求
  getExpertProfile()
    .then((p) => { expertAuditStatus.value = p?.auditStatus || '' })
    .catch(() => { expertAuditStatus.value = '' })
}

/* ---------- 消息提醒（顶栏铃铛） ---------- */
const msgOpen = ref(false)
const {
  items: msgItems,
  total: msgTotal,
  pendingTotal: msgPendingTotal,
  loading: msgLoading,
  load: loadMessages,
  unreadOf,
  markAllRead,
  markRead
} = useMessages()
// 进页面就拉一次，铃铛上的角标不必等用户点开才出现
loadMessages()

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

/**
 * 退出登录。
 *
 * ★ `userStore.logout()` 必须连 activeRole 一起清（store 内部已处理），
 *   否则下次登录另一个角色时，残留的 activeRole 会让「身份切换」显示成上一个账号的。
 * 登出后回登录页，不带 redirect —— 此时已无登录态，回跳目标没有意义。
 */
function handleLogout() {
  userStore.logout()
  router.push('/login')
}

/**
 * 点消息条目：关掉浮层再跳，否则浮层会挂在登录页/目标页上一瞬。
 * ★ 同时把这条标记为已读 —— 用户已经点开看过了，铃铛不该继续为它亮着。
 *   条目本身仍留在列表里（待办不会因为「看过」就消失）。
 */
function goMessage(m) {
  msgOpen.value = false
  if (m?.key) markRead(m.key)
  if (m?.path) router.push(m.path)
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
.user-area { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
/* 退出登录：常驻按钮（原先进下拉最里层，用户反馈藏得太深）。
   2026-10-13 去掉文字后改成 34×34 的方形图标靶位，与右边铃铛同尺寸、同圆角 ——
   两个纯图标按钮挨在一起，尺寸不一致会立刻显得是「拼出来的」。
   plain 保留：它仍然不该抢头像的视觉重心。 */
.logout-btn { width: 34px; height: 34px; padding: 0; border-radius: 10px; }
.logout-btn :deep(.rxt-icon) { margin-right: 0; }
/* 消息铃铛：方形图标按钮。用 button 而不是 el-button —— 这里要的是一个纯图标靶位，
   套 el-button 会多带一层内边距与文字基线，34px 的方块会缩成 28px。 */
.icon-btn {
  display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; padding: 0;
  border: 1px solid var(--rxt-border); border-radius: 10px;
  background: var(--el-bg-color); color: var(--rxt-ink-weak);
  cursor: pointer; transition: color .2s, border-color .2s;
}
.icon-btn:hover { color: var(--rxt-primary); border-color: var(--rxt-primary); }
/* el-badge 是 inline-block，会和按钮之间多出一层行高，压成 inline-flex */
.msg-badge { display: inline-flex; }
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

<!--
  消息浮层的样式必须放**非 scoped** 块：el-popover 的内容默认 teleport 到 body，
  带的是它自己的作用域标记，写在上面那个 scoped 块里一条都吃不到
  （与 AuthShell 给插槽内容用的 .auth-* 是同一个道理）。
-->
<style>
/* 清掉 EP 默认的 12px 内边距，让面板自己控制留白 */
.el-popover.msg-popover { padding: 0; }

.msg-panel { width: 100%; box-sizing: border-box; }

.msg-head {
  display: flex; align-items: baseline; justify-content: space-between; gap: 12px;
  padding: 13px 16px 11px; border-bottom: 1px solid var(--rxt-border);
}
.msg-head strong { font-size: 14px; }
.msg-head span { font-size: 12px; color: var(--rxt-ink-weak); }

.msg-list { margin: 0; padding: 4px 0; list-style: none; max-height: 340px; overflow-y: auto; }
.msg-item {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 10px 16px; cursor: pointer; transition: background .15s;
}
.msg-item:hover { background: var(--el-color-primary-light-9); }
.msg-dot {
  flex: none; width: 7px; height: 7px; margin-top: 6px;
  border-radius: 50%; background: var(--rxt-ink-weak);
}
.msg-dot.is-warning { background: var(--el-color-warning); }
.msg-dot.is-danger  { background: var(--el-color-danger); }
.msg-dot.is-primary { background: var(--el-color-primary); }
.msg-dot.is-success { background: var(--el-color-success); }
.msg-body { flex: 1; min-width: 0; }
.msg-body strong { display: block; font-size: 13px; font-weight: 600; }
.msg-body strong em {
  margin-left: 5px; font-style: normal; font-size: 12px; font-weight: 700;
  color: var(--el-color-danger); font-variant-numeric: tabular-nums;
}
.msg-body small {
  display: block; margin-top: 3px; font-size: 12px; color: var(--rxt-ink-weak);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.msg-go { flex: none; margin-top: 2px; color: var(--rxt-primary); }

.msg-empty { margin: 0; padding: 30px 16px; text-align: center; font-size: 13px; color: var(--rxt-ink-weak); }

/* 已读的待办：圆点褪成中性色。★ 只弱化圆点，不隐藏条目、不降整行透明度 ——
   「看过了」不等于「不用做了」，条目仍要能被点进去 */
.msg-item.is-read .msg-dot { background: var(--el-border-color); }
.msg-item.is-read .msg-body strong { color: var(--rxt-ink-weak); }

.msg-foot {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 9px 12px 9px 16px; border-top: 1px solid var(--rxt-border);
  font-size: 12px; color: var(--rxt-ink-weak);
}
</style>
