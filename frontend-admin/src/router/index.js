import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/views/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'user/list', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'audit/product', component: () => import('@/views/audit/ProductAudit.vue'), meta: { title: '商品审核' } },
      { path: 'audit/knowledge', component: () => import('@/views/audit/KnowledgeAudit.vue'), meta: { title: '知识审核' } },
      { path: 'audit/comment', component: () => import('@/views/audit/CommentAudit.vue'), meta: { title: '评论审核' } },
      { path: 'finance/manage', component: () => import('@/views/finance/FinanceManage.vue'), meta: { title: '融资管理' } },
      // 资源管理：与上面的「内容审核」分开 —— 审核是给内容下结论（写 t_audit 留痕），
      // 资源管理是维护主体资料与存储对象，两者职责不同，导航上也别合并
      { path: 'expert/manage', component: () => import('@/views/expert/ExpertManage.vue'), meta: { title: '专家资源管理' } },
      { path: 'media/manage', component: () => import('@/views/media/MediaManage.vue'), meta: { title: '图片资源管理' } }
    ]
  }
]

const router = createRouter({
  // BASE_URL 来自 vite 的 base 配置（部署到 /admin/ 下时为 '/admin/'，本地开发为 '/'）。
  // 写死 '/' 的话，Nginx 分流部署后刷新 /admin/user/list 会被当成根路径路由匹配不上。
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

/**
 * 守卫做两件事：**有没有登录** + **是不是管理员**。
 *
 * ★ 2026-10-10 补第二件事。此前只判 `getToken()`，而 token 不区分角色：
 *   `/auth/login` 是业务端与后台共用的同一个接口，农户 / 采购方 / 专家 /
 *   银行用户都能拿到合法 token，于是**任何角色都能登进后台、看到管理界面**。
 *   数据面当时是靠后端 `requireAdmin()` 逐个接口 403 挡住的 —— 人已经进来了，
 *   只是每页都空。这不符合「后台只给管理员用」的定位。
 *
 * ★ 不需要「本轮是否已验过角色」的模块级标记：判据直接用 store 的 `profile`
 *   是否为空即可 —— 整页刷新后 store 重建、profile 为 null，会重新验一次；
 *   之后的导航 profile 已在内存里，不再重复打接口。
 *   用标记的写法要多维护「在哪复位」这一处，忘了复位就会在
 *   「退出登录 → 手改 localStorage → 再直访」这条路径上误放行。
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'

  const userStore = useUserStore()

  // 角色以**服务端**为准：profile 为空（整页刷新后 / 刚退出登录）时先取一次。
  // 不能信 localStorage 里的角色，那样手改一下就绕过去了。
  try {
    await userStore.ensureProfile()
  } catch {
    // 401 已由 api/request.js 清 token 并跳登录页；其余错误一并按「未通过」处理
    return '/login'
  }

  if (!userStore.isAdmin) {
    userStore.logout()
    return { path: '/login', query: { denied: '1' } }
  }
  return true
})

export default router
