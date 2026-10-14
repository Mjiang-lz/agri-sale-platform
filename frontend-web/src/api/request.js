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

/**
 * 把后端的兜底文案翻成人话。
 *
 * ⚠️ 为什么需要它：后端 `GlobalExceptionHandler` 把所有未识别的异常
 * （含 `HttpMessageNotReadableException`、缺表导致的 SQLException 等）
 * 一律翻成「系统繁忙，请稍后重试」。这句话**没有给出任何可行动信息** ——
 * 用户看到只会以为是网络问题然后反复重试，而真实原因（数据表不存在、
 * 日期格式不对）永远传不到前端，排查只能靠人肉复现。
 *
 * 这里按路径给出定位方向，而不是把「系统繁忙」换个说法：
 * · address相关 → 指向收货地址表（历史上 t_address 未建导致整页 500）
 * · trade/order → 指向订单装配链路
 * 其余统一提示「服务端异常」，并明确告知**重试无用**，避免用户空转。
 *
 * ⚠️ 这是**提示层**的改善，不是修复。真正的问题是后端要补
 * `HttpMessageNotReadableException` 等分支、把真实异常原因透出来。
 */
function explainServerError(path, fallback) {
  const p = String(path || '')

  if (p.includes('/address')) {
    return '收货地址功能暂时不可用（服务端异常，重试无效）。请联系管理员检查收货地址数据表。'
  }
  if (p.includes('/trade/order') || p.includes('/trade/cart')) {
    return '交易数据加载失败（服务端异常，重试无效）。请联系管理员查看后台日志。'
  }
  if (p.includes('/demand')) {
    return '求购需求提交失败（服务端异常，重试无效）。请检查填写内容格式是否正确。'
  }
  // 其余场景：至少把「重试无效」讲清楚，「系统繁忙」会让人以为再点一次就能好
  return `${fallback}（服务端异常，重试无效；请稍后再试或联系管理员）`
}

/**
 * 后端「没有具体信息」的兜底文案。
 *
 * ★ 为什么要有这个判断：本项目的 `ResultCode.BIZ_ERROR` 就是 **500**，
 *   而后端的 `BizException` 大量用来表达**用户自己能改正**的业务校验
 *   （「验证码不正确或已过期」「该手机号已注册」……）。如果凡是 code 500 都套上
 *   「服务端异常，重试无效；请联系管理员」，就等于把一个「看错了一个字」的问题
 *   宣布成系统故障 —— 用户会去重试、去等、去联系管理员，唯独不会去改输入。
 *
 * 所以只有后端给的是**通用兜底**（等于没说）时才做翻译；后端已经说清楚的，
 * 原样透出，那句话本身就是最好的提示。
 */
const GENERIC_SERVER_MESSAGES = new Set([
  '业务处理失败', // ResultCode.BIZ_ERROR 的默认文案
  '系统繁忙，请稍后重试', // ResultCode.SYSTEM_ERROR
  '请求失败' // 本文件的兜底
])

function isGenericServerMessage(msg) {
  return !msg || GENERIC_SERVER_MESSAGES.has(msg)
}

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

    const raw = body.message || '请求失败'
    // 500 走服务端异常的翻译；其余（400 参数错/403 无权限等）后端文案本身已经足够具体。
    // ★ 且只翻译「通用兜底」文案 —— 详见 isGenericServerMessage 的说明。
    const message = body.code === 500 && isGenericServerMessage(raw)
      ? explainServerError(response.config?.url, raw)
      : raw

    // silent:true 的请求由调用方自己渲染错误态，不弹全局 toast。
    // 首页一次并发拉五组数据，若接口都断，叠加的 toast 会糊住页头，
    // 而那里要的是干净的降级（订单为 0 是合法状态），不是提示。
    if (!response.config?.silent) {
      ElMessage.error(message)
    }
    // 标记「已提示过」：页面 catch 里不要再弹一次，否则同一次失败会刷出两条提示。
    // 页面拿到这个标记后应改为渲染行内错误态（带重试按钮），而不是再弹 toast。
    const err = new Error(message)
    err.notified = true
    // 把原始文案也留着：页面若要渲染行内错误态，用它比用翻译后的长句更紧凑。
    err.rawMessage = raw
    // 把业务码带出去：页面需要区分「404 = 这东西真的没了（重试永远失败）」与
    // 「其他 = 暂时性故障（值得给重试按钮）」。以前只有 message 可用，只能拿文案做
    // 字符串匹配 —— 后端改一个字就静默失效。HTTP 状态码也带一份，方便排查。
    err.code = body.code
    err.httpStatus = response.status
    return Promise.reject(err)
  },
  (error) => {
    // 连不上后端（服务没启动 / 端口不对 / 网断）与「服务端 500」是两回事，
    // 但对用户来说都表现为「功能用不了」。这里明确区分：
    // 前者重试有意义（等服务起来），后者重试没用（问题在服务端）。
    const message = error.code === 'ERR_NETWORK' || error.code === 'ECONNABORTED'
      ? '无法连接服务器，请确认后端服务已启动后重试'
      : '网络异常，请稍后重试'
    if (!error.config?.silent) {
      ElMessage.error(message)
    }
    // 同上：网络层错误已统一提示过，页面只负责渲染错误态
    error.notified = true
    return Promise.reject(error)
  }
)

export default request
