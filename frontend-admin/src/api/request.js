import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, removeToken } from '@/utils/auth'

/**
 * 统一请求封装：自动附带 JWT、统一解包 Result、统一错误提示。
 *
 * ⚠️ token 一律走 `utils/auth` 的读写函数，**不要再在这里硬编码键名** ——
 * 键名一旦有两处来源，改其中一处就会出现「拦截器读不到 token、
 * 每个请求都变成匿名」这种静默失联，且只在运行期暴露。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code === 200) {
      return body.data
    }
    if (body.code === 401) {
      removeToken()
      router.push('/login')
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
