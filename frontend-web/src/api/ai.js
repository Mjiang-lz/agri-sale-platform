import request from './request'

/**
 * AI 助手接口（后端 `AiController`，前缀 `/ai`）。
 *
 * 设计要点见 `docs/方案-专家问答接入DeepSeek.md`：浏览器只与本站的 `/api/ai/**` 通信，
 * DeepSeek 的 API Key 只存在于服务端进程内存中，永远不会出现在前端产物里。
 */

/**
 * 查询 AI 助手是否可用。
 *
 * ★ 用 `silent`：这个接口在页面挂载时就会调，而未配置密钥的环境（队友本地、未开开关的服务器）
 *   必然返回「未开启」。那是**正常状态**，不是错误，不该每次进页面都弹一条红提示。
 *   调用方拿到异常时按「不可用」处理即可。
 */
export function getAiStatus() {
  return request.get('/ai/status', { silent: true })
}

/**
 * 向 AI 助手提问，直接返回参考意见（不落库）。
 *
 * ★ 单独放宽超时到 60 秒：全局 `timeout: 15000` 是按普通 CRUD 定的，
 *   而大模型推理本身就可能要十几秒。沿用全局值会在服务端还在算的时候把请求掐掉 ——
 *   用户看到「网络异常」，而服务端那次调用其实已经发生并计费了。
 *
 * @param {{content: string, history?: Array<{role: string, content: string}>}} payload
 *        history 只回传最近几轮；服务端还会再截断一次，不要在这里省略
 */
export function askAi(payload) {
  return request.post('/ai/qa/ask', payload, { timeout: 60000 })
}
