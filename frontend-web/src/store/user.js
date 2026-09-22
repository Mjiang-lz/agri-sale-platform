import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'

const ACTIVE_ROLE_KEY = 'rxt_active_role'
const SWITCHABLE_ROLES = ['农户', '采购方']

/**
 * 登录态、当前用户信息和业务端当前身份。
 *
 * activeRole 只影响业务端导航展示，后端接口权限始终以 JWT 中的真实角色为准。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const profile = ref(null)
  const activeRole = ref(localStorage.getItem(ACTIVE_ROLE_KEY) || '农户')

  function setActiveRole(role) {
    if (!SWITCHABLE_ROLES.includes(role)) return
    activeRole.value = role
    localStorage.setItem(ACTIVE_ROLE_KEY, role)
  }

  function saveLogin(data) {
    token.value = data.token
    setToken(data.token)
    profile.value = data
    setActiveRole(data.roleName === '采购方' ? '采购方' : '农户')
  }

  function logout() {
    token.value = ''
    profile.value = null
    activeRole.value = '农户'
    localStorage.removeItem(ACTIVE_ROLE_KEY)
    removeToken()
  }

  return { token, profile, activeRole, setActiveRole, saveLogin, logout }
})
