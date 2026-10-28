import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'

const ACTIVE_ROLE_KEY = 'rxt_active_role'
const PROFILE_KEY = 'rxt_user_profile'
const SWITCHABLE_ROLES = ['农户', '采购方']

function readProfile() {
  try {
    return JSON.parse(localStorage.getItem(PROFILE_KEY) || 'null')
  } catch {
    return null
  }
}

/**
 * 登录态、当前用户信息、后端菜单权限和业务端当前身份。
 *
 * menuPerms 来自后端 LoginVO，是菜单展示的权威数据源。
 * activeRole 只用于农户/采购方演示切换，不改变 JWT 或接口权限。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const profile = ref(readProfile())
  const menuPerms = ref(profile.value?.menuPerms || '')
  const activeRole = ref(localStorage.getItem(ACTIVE_ROLE_KEY) || profile.value?.roleName || '农户')

  function setMenuPerms(value) {
    menuPerms.value = value || ''
    localStorage.setItem(PROFILE_KEY, JSON.stringify({ ...(profile.value || {}), menuPerms: menuPerms.value }))
  }

  function setActiveRole(role) {
    if (!SWITCHABLE_ROLES.includes(role)) return
    activeRole.value = role
    localStorage.setItem(ACTIVE_ROLE_KEY, role)
  }

  /**
   * 局部更新本地缓存的登录资料（只改本地，不回写后端）。
   *
   * 用途：个人中心改完昵称 / 头像后，顶栏那处「昵称」要立刻跟着变。
   * profile 是**登录那一刻**的快照，不同步的话用户改完名字得重新登录
   * 才看得到，会以为没保存成功。
   *
   * ⚠️ 只传后端确实改成功的字段 —— 前端不能抢在后端之前改写本地事实。
   */
  function setProfile(patch) {
    if (!patch || typeof patch !== 'object') return
    profile.value = { ...(profile.value || {}), ...patch }
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value))
  }

  function saveLogin(data) {
    token.value = data.token
    setToken(data.token)
    profile.value = data
    menuPerms.value = data.menuPerms || ''
    activeRole.value = data.roleName || '农户'
    localStorage.setItem(PROFILE_KEY, JSON.stringify(data))
    localStorage.setItem(ACTIVE_ROLE_KEY, activeRole.value)
  }

  function logout() {
    token.value = ''
    profile.value = null
    menuPerms.value = ''
    activeRole.value = '农户'
    localStorage.removeItem(PROFILE_KEY)
    localStorage.removeItem(ACTIVE_ROLE_KEY)
    removeToken()
  }

  return { token, profile, menuPerms, activeRole, setMenuPerms, setActiveRole, setProfile, saveLogin, logout }
})
