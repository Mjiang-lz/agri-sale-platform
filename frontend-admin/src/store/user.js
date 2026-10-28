import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getRole, getToken, removeToken, setRole, setToken } from '@/utils/auth'

/**
 * 平台管理员角色名。
 * 与后端 `RoleConstant.ADMIN = "管理员"` 是同一个字面量，改一处必须改两处。
 */
export const ADMIN_ROLE = '管理员'

/**
 * 登录态与当前用户信息。
 *
 * ★ 本应用**只对管理员开放**（2026-10-10 补）：
 *   2026-10-14 起后台专用入口 `/auth/admin/login` 已在服务端限定管理员
 *   （非管理员直接 403），但前端仍再校一次 —— token 本身不区分角色，
 *   历史遗留的、或手工写进 localStorage 的 token 不会经过那道服务端闸口。
 *   登录页与路由守卫都要额外校一次角色，判定统一走这里的 `isAdmin`。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const profile = ref(null)

  /** 角色：优先用内存里的 profile，整页刷新后回落到 localStorage 那份 */
  const role = computed(() => profile.value?.roleName || getRole() || '')
  const isAdmin = computed(() => role.value === ADMIN_ROLE)

  /**
   * 确保 profile 已从**服务端**取回。
   *
   * 守卫用它来判断「要不要先验一次角色」，判据是 `profile` 本身是否为空：
   *   · 整页刷新 → profile 是 null → 取一次；
   *   · 退出登录 → logout() 把 profile 置回 null → 下次进管理页会重新取。
   *
   * 这里刻意**不放一个「本轮是否已验过角色」的布尔标记**。
   * 那种写法要额外记得在 logout() 里复位，忘了复位就会出现：
   * 退出登录后同一次页面会话里的下一次导航沿用「上一位用户已通过」的结论，
   * 而 `role` 此时已回落到 localStorage 的 getRole() —— 伪造一下就误放行。
   * 用 `profile` 当判据则**不存在**需要复位的第二处状态。
   */
  async function ensureProfile() {
    if (profile.value) return profile.value
    return fetchProfile()
  }

  function saveLogin(data) {
    token.value = data.token
    setToken(data.token)
    profile.value = data
    setRole(data.roleName)
  }

  /**
   * 拉一次当前登录者资料（含 roleName）。
   *
   * 守卫在整页刷新后靠它把角色补回来，所以这里**必须真的打一次接口**，
   * 不能拿 localStorage 里的角色顶替 —— 否则手改 localStorage 就能绕过守卫。
   *
   * 用动态 import 而不是文件顶部的静态 import：`api/request.js` 里有
   * `import router from '@/router'`，而 router 又引本模块，
   * 顶部静态引入会让 router ↔ request 形成模块环，环里取默认导出容易拿到 undefined。
   * 动态 import 到调用时才解析，绕开这个问题。
   */
  async function fetchProfile() {
    const { getCurrentProfile } = await import('@/api/user')
    const latest = await getCurrentProfile()
    profile.value = latest
    setRole(latest?.roleName)
    return latest
  }

  function logout() {
    token.value = ''
    // profile 必须置回 null —— 守卫正是靠它判断「要不要重新向服务端验角色」。
    // 只清 token 不清 profile，退出登录后的下一次导航就会沿用上一位用户的角色。
    profile.value = null
    removeToken()
  }

  return { token, profile, role, isAdmin, saveLogin, fetchProfile, ensureProfile, logout }
})
