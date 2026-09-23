import request from './request'

/**
 * 专家问答与专家预约接口文件。
 *
 * 对应页面：
 * - frontend-web/src/views/expert/Qa.vue
 * - frontend-web/src/views/expert/Appointment.vue
 *
 * 后端基础路径已由 request.js 设置为 /api：
 * - POST /api/expert/qa/ask             提交问题
 * - GET  /api/expert/qa/my              查询我的问题
 * - GET  /api/expert/qa/list            查询某个问题的回答
 * - POST /api/expert/appointment/submit 提交专家预约
 */

/** 专家问答页：提交问题。expertId 为空表示公开提问。 */
export function askQuestion(payload) {
  return request.post('/expert/qa/ask', payload)
}

/** 专家问答页：查询当前农户自己的提问。 */
export function getMyQuestions() {
  return request.get('/expert/qa/my')
}

/** 专家问答页：查询指定问题的专家回答。 */
export function getQuestionAnswers(questionId) {
  return request.get('/expert/qa/list', { params: { questionId } })
}

/** 专家预约页：提交线下预约，请求体对应 AppointmentDTO。 */
export function submitAppointment(payload) {
  return request.post('/expert/appointment/submit', payload)
}

/** 专家工作台：查询当前专家可回复的待处理问题。 */
export function getExpertPendingQuestions() {
  return request.get('/expert/qa/pending')
}

/** 专家工作台：提交专家回答。 */
export function submitExpertAnswer(payload) {
  return request.post('/expert/qa/answer', payload)
}
