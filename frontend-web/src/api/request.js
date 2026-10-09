import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * 统一请求封装：自动附带 JWT、统一解包 Result、统一错误提示。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('rxt_token')
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
      localStorage.removeItem('rxt_token')
      router.push('/login')
    }
    const message = body.message || '请求失败'
    ElMessage.error(message)
    // 标记「已提示过」：页面 catch 里不要再弹一次，否则同一次失败会刷出两条提示。
    // 页面拿到这个标记后应改为渲染行内错误态（带重试按钮），而不是再弹 toast。
    const err = new Error(message)
    err.notified = true
    return Promise.reject(err)
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    // 同上：网络层错误已统一提示过，页面只负责渲染错误态
    error.notified = true
    return Promise.reject(error)
  }
)

export default request
