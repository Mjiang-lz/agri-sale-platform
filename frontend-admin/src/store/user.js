import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'

/**
 * 登录态与当前用户信息。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const profile = ref(null)

  function saveLogin(data) {
    token.value = data.token
    setToken(data.token)
    profile.value = data
  }

  function logout() {
    token.value = ''
    profile.value = null
    removeToken()
  }

  return { token, profile, saveLogin, logout }
})
