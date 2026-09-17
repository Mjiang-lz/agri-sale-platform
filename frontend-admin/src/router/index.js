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
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'
  return true
})

export default router
