import request from './request'

/**
 * 认证相关接口。
 *
 * 登录 / 注册在页面里是直接 `request.post('/auth/login', form)` 写的
 * （Login.vue / Register.vue），没有单独包一层 —— 两处各一行，包一层反而要多跳一次。
 * 这里只放**要复用的**：图形验证码。
 */

/**
 * 取一张图形验证码。
 *
 * 后端返回 { captchaKey, captchaImage }，captchaImage 是完整 data URL，
 * 前端直接塞 <img src>，不必再拼域名（换服务器/IP 时历史页面不会图裂）。
 *
 * ⚠️ **每个 key 只能用一次**，无论验证对错。所以：
 *   · 「点击图片刷新」必须重新请求本接口，不能重复提交同一个 key；
 *   · 登录/注册失败后必须刷新，否则用户下一次提交还是带着已作废的 key。
 */
export function getCaptcha() {
  return request.post('/auth/captcha')
}
