import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

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
      { path: 'finance/manage', component: () => import('@/views/finance/FinanceManage.vue'), meta: { title: '融资管理' } }
    ]
  }
]

const router = createRouter({
  // BASE_URL 来自 vite 的 base 配置（部署到 /admin/ 下时为 '/admin/'，本地开发为 '/'）。
  // 写死 '/' 的话，Nginx 分流部署后刷新 /admin/user/list 会被当成根路径路由匹配不上。
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'
  return true
})

export default router
