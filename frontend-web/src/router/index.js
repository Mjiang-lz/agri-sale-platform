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
      { path: 'home', component: () => import('@/views/BizHome.vue'), meta: { title: '首页' } },
      // 商品货源与商品详情对游客开放（LX-4 ③ 的裁决）：后端这两个接口本来就在
      // PublicEndpoints 白名单里，前端却由全局守卫一律打回登录页，
      // 结果是「游客能调接口、却看不到页面」。路由 meta.public 与白名单保持一致。
      { path: 'trade/products', component: () => import('@/views/trade/ProductList.vue'), meta: { title: '商品货源', public: true } },
      { path: 'trade/product/:id', component: () => import('@/views/trade/ProductDetail.vue'), meta: { title: '商品详情', public: true } },
      { path: 'trade/demand', component: () => import('@/views/trade/DemandList.vue'), meta: { title: '求购需求' } },
      // 我的商品：农户发布与上下架的入口。
      // 后端 /trade/my-products 等四个接口早在骨架期就实现了，前端一直缺这一页，
      // 于是「农户登录后没有任何入口发布自己的农产品」——供给侧闭环是断的。
      { path: 'trade/my-products', component: () => import('@/views/trade/MyProducts.vue'), meta: { title: '我的商品' } },
      { path: 'trade/cart', component: () => import('@/views/trade/Cart.vue'), meta: { title: '购物车' } },
      // 结算确认页。购物车点「结算」跳这里 —— 下单必须带收货地址，
      // 所以地址选择与商品核对得有独立页面，弹窗里塞不下。
      { path: 'trade/checkout', component: () => import('@/views/trade/Checkout.vue'), meta: { title: '确认订单' } },
      { path: 'trade/orders', component: () => import('@/views/trade/OrderList.vue'), meta: { title: '我的订单' } },
      { path: 'finance/apply', component: () => import('@/views/finance/FinanceApply.vue'), meta: { title: '融资申请' } },
      { path: 'finance/my', component: () => import('@/views/finance/MyApplies.vue'), meta: { title: '我的融资' } },
      // 融资审批（银行用户视角）。与上面的「我的融资」是两个模块：
      // /finance/* 是申请人看自己的，/loan-admin/* 是银行看别人提交的。
      { path: 'finance/bank-review', component: () => import('@/views/finance/BankReview.vue'), meta: { title: '融资审批' } },
      { path: 'expert/knowledge', component: () => import('@/views/expert/Knowledge.vue'), meta: { title: '农业知识' } },
      { path: 'expert/qa', component: () => import('@/views/expert/Qa.vue'), meta: { title: '专家问答' } },
      { path: 'expert/appointment', component: () => import('@/views/expert/Appointment.vue'), meta: { title: '专家预约' } },
      { path: 'user/profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'user/address', component: () => import('@/views/user/Address.vue'), meta: { title: '收货地址' } }
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
  // 需要补拉的两种情况：
  //   1) 缺 menuPerms —— 老会话（rxt_user_profile 是后加的），菜单会只剩「首页」；
  //   2) profile 里**没有 avatarUrl 这个字段** —— 登录接口（LoginVO）此前不下发头像，
  //      导致 saveLogin 用登录响应整体覆盖 profile 时把头像地址冲掉，顶栏永远显示首字。
  //      补一次 getProfile() 就回来了（UserVO 是带 avatarUrl 的）。
  // ⚠️ 用 `=== undefined` 而不是 falsy 判断：avatarUrl 合法地可以是 null（从没设过头像），
  //    那种情况下不该每次导航都去打接口。
  const needHydrate = !userStore.menuPerms || userStore.profile?.avatarUrl === undefined
  if (needHydrate && !hydrateSettled) {
    try {
      // 用动态 import 而不是文件顶部的静态 import：api/request.js 里有
      // `import router from '@/router'`，顶部引入 api/user 会让 router ↔ request 形成模块环，
      // 环里取默认导出容易拿到 undefined。动态 import 到调用时才解析，绕开这个问题。
      const { getProfile } = await import('@/api/user')
      // ⚠️ getProfile() 返回的是 UserVO，**没有 token 字段**（有 token 的是登录接口的 LoginVO）。
      // 直接 saveLogin(它) 会让 saveLogin 里的 setToken(data.token) 写入 undefined ——
      // localStorage 落下去的是**字面字符串 "undefined"**，下一次请求带着 `Bearer undefined`
      // 换回 401，拦截器随即清掉 token 并踢回登录页。
      // 也就是说：这段「自愈」恰好把它本意要救的那类用户（有 token、无 profile）登出了。
      // 所以合并时要把当前仍然有效的 token 显式兜回去（守卫在上面已确认 getToken() 非空）。
      const latest = await getProfile()
      userStore.saveLogin({ ...userStore.profile, ...latest, token: getToken() })
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
