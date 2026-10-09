import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
      { path: 'trade/products', component: () => import('@/views/trade/ProductList.vue'), meta: { title: '商品货源' } },
      { path: 'trade/product/:id', component: () => import('@/views/trade/ProductDetail.vue'), meta: { title: '商品详情' } },
      { path: 'trade/demand', component: () => import('@/views/trade/DemandList.vue'), meta: { title: '求购需求' } },
      { path: 'trade/cart', component: () => import('@/views/trade/Cart.vue'), meta: { title: '购物车' } },
      { path: 'trade/orders', component: () => import('@/views/trade/OrderList.vue'), meta: { title: '我的订单' } },
      { path: 'finance/apply', component: () => import('@/views/finance/FinanceApply.vue'), meta: { title: '融资申请' } },
      { path: 'finance/my', component: () => import('@/views/finance/MyApplies.vue'), meta: { title: '我的融资' } },
      { path: 'expert/knowledge', component: () => import('@/views/expert/Knowledge.vue'), meta: { title: '农业知识' } },
      { path: 'expert/qa', component: () => import('@/views/expert/Qa.vue'), meta: { title: '专家问答' } },
      { path: 'expert/appointment', component: () => import('@/views/expert/Appointment.vue'), meta: { title: '专家预约' } },
      { path: 'user/profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人中心' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) {
    // 未登录：把「本来想去哪」带过去，登录后回跳。
    // 不带的话，游客从落地页/外链点具体业务页（如 /trade/products）会落回 /home，目的地丢失
    // —— 落地页 Section4 四个 tab 与 /shop 的商品卡片都走这条路径。
    // 默认首页（/、/home）不带，避免 URL 里多一串没意义的 query。
    const dest = to.fullPath
    if (dest === '/' || dest === '/home') return '/login'
    return { path: '/login', query: { redirect: dest } }
  }
  return true
})

export default router
