/**
 * 管理后台的 token 存储键。
 *
 * 刻意**不复用**业务端的 `rxt_token`：部署态下 `deploy/nginx.conf` 把三个前端挂在
 * 同一个源的三个路径下（`/` 落地页、`/app/` 业务端、`/admin/` 后台），
 * localStorage 是**按源隔离、不按路径隔离**的 —— 沿用同一个键会导致：
 *   ① 在后台登录会覆盖业务端的 token；
 *   ② 任一侧退出登录（removeToken）会把另一侧静默登出。
 * 两者本就是两套互不相干的会话，键也必须分开。
 */
const TOKEN_KEY = 'rxt_admin_token'

/**
 * 当前登录者的**角色名**。
 *
 * 为什么角色也要落盘：路由守卫在**整页刷新**之后仍要能判断「是不是管理员」，
 * 而 pinia 里的 profile 是内存态、刷新即丢。只存 token 的话，刷新页面时
 * 守卫无从判断角色，就只能放行 —— 那等于这道闸门不存在。
 *
 * ★ 这一层是**体验闸**，不是安全边界：localStorage 用户可手改。
 * 真正的边界在后端 —— `/api/admin/**` 的服务层已统一 `requireAdmin()`
 * （实测：农户 / 采购方 / 专家 / 银行用户打这四个接口全部返回 `code:403`）。
 * 手改这里最多得到一个「处处 403 的空壳」。
 */
const ROLE_KEY = 'rxt_admin_role'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 清登录态。
 * **角色一并清掉**：只清 token 会留下「已经登出、但仍被判为管理员」的脏状态，
 * 下次带着别的角色进后台时守卫会误放行。
 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY)
}

export function setRole(role) {
  if (role) {
    localStorage.setItem(ROLE_KEY, role)
  } else {
    localStorage.removeItem(ROLE_KEY)
  }
}
