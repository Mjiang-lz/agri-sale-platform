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

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}
