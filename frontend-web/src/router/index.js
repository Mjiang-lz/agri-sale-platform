import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
      // 商品货源与商品详情对游客开放（LX-4 ③ 的裁决）：后端这两个接口本来就在
      // PublicEndpoints 白名单里，前端却由全局守卫一律打回登录页，
      // 结果是「游客能调接口、却看不到页面」。路由 meta.public 与白名单保持一致。
      { path: 'trade/products', component: () => import('@/views/trade/ProductList.vue'), meta: { title: '商品货源', public: true } },
      { path: 'trade/product/:id', component: () => import('@/views/trade/ProductDetail.vue'), meta: { title: '商品详情', public: true } },
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
  // BASE_URL 来自 vite 的 base 配置（部署到 /app/ 下时为 '/app/'，本地开发为 '/'）。
  // 写死 '/' 的话，Nginx 分流部署后刷新 /app/trade/products 会被当成根路径路由匹配不上。
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

/**
 * 跳登录页，并把「本来想去哪」带过去，登录后回跳。
 * 不带的话，游客从落地页/外链点具体业务页（如 /trade/products）会落回 /home，目的地丢失
 * —— 落地页 Section4 四个 tab 与 /shop 的商品卡片都走这条路径。
 * 默认首页（/、/home）不带，避免 URL 里多一串没意义的 query。
 */
function toLogin(to) {
  const dest = to.fullPath
  if (dest === '/' || dest === '/home') return '/login'
  return { path: '/login', query: { redirect: dest } }
}

// CY-4：老会话只有 token、没有 profile（`rxt_user_profile` 与 menuPerms 是后加进来的），
// 于是 MainLayout 四个 canSeeXxx 全为 false —— 只剩「首页」一个菜单，且没有任何提示，
// 只能靠「退出重登」自愈。每个老用户都会撞上，演示当天尤其扎眼。
//
// 处置上不走「直接清登录态踢回登录页」那条路：token 是 JWT，本地缺 profile 并不代表它失效了，
// 为了一个纯本地的缺项逼用户重新输账号密码，代价不对等。改为拿 token 去补拉一次资料即自愈。
//
// 每次页面加载只成功尝试一次，避免后端确实返回空 menuPerms 时（那是合法结果）反复打接口。
let hydrateSettled = false

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!getToken()) return toLogin(to)

  const userStore = useUserStore()
  if (!userStore.menuPerms && !hydrateSettled) {
    try {
      // 用动态 import 而不是文件顶部的静态 import：api/request.js 里有
      // `import router from '@/router'`，顶部引入 api/user 会让 router ↔ request 形成模块环，
      // 环里取默认导出容易拿到 undefined。动态 import 到调用时才解析，绕开这个问题。
      const { getProfile } = await import('@/api/user')
      userStore.saveLogin(await getProfile())
      hydrateSettled = true
    } catch {
      // 要区分「会话真的失效」和「只是网络抖了一下」：
      // request 拦截器判定 401 时会清掉持久化的 token，其余错误不会。
      // 只有前者才可以把用户踢回登录页 —— 一次网络抖动不该毁掉仍然有效的会话；
      // 后者不置位 hydrateSettled，下一次导航会再补一次。
      if (!getToken()) {
        hydrateSettled = true
        userStore.logout()
        return toLogin(to)
      }
    }
  }
  return true
})

export default router
