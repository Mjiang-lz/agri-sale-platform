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
 * - GET  /api/expert/experts            查询专家一览（首页「专家一览」用）
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

/**
 * 专家预约页：查询我提交过的全部预约（REQ-11）。
 *
 * 后端按登录态取 farmerId，不接受前端传归属人 —— 不要试图在这里加 userId 参数。
 * 返回 AppointmentVO 数组：
 * { apptId, expertId, expertName, expertDomain, apptTime, apptTopic,
 *   apptStatus, remark, createTime }
 * 其中 remark 是专家处理意见或取消原因，配合 apptStatus 构成「结果通知」。
 */
export function getMyAppointments() {
  return request.get('/expert/appointment/my')
}

/**
 * 专家一览（概设 UI-EXP-03「专家介绍」）。
 *
 * @param {string} [domain] 专业领域，用于筛选；不传或传空串表示不限领域
 * @returns {Promise<Array>} ExpertVO 数组：
 *   { expertId, userId, realName, avatarUrl, expertDomain, introduction, workloadStat }
 *
 * 后端只返回「已通过审核」的专家，且 realName / avatarUrl 已在服务端一次装配好，
 * 调用方不需要（也不应该）再对每行补一次用户信息 —— 既有的逐行补全写法会让
 * 列表接口退化成 N+1，之前首页正是踩在这上面。
 */
export function getExperts(domain) {
  return request.get('/expert/experts', {
    params: domain ? { domain } : {}
  })
}

/** 专家工作台：查询当前专家可回复的待处理问题。 */
export function getExpertPendingQuestions() {
  return request.get('/expert/qa/pending')
}

/** 专家工作台：提交专家回答。 */
export function submitExpertAnswer(payload) {
  return request.post('/expert/qa/answer', payload)
}

/** 专家工作台：查询当前专家自己的预约列表。 */
export function getExpertAppointments(status) {
  return request.get('/expert/appointment/expert', {
    params: status && status !== '全部' ? { status } : {}
  })
}

/** 专家工作台：接受或拒绝预约。 */
export function handleExpertAppointment(apptId, payload) {
  return request.post(`/expert/appointment/${apptId}/handle`, payload)
}

/**
 * 个人中心：专家查看**本人**档案（《SRS》个人信息 → 专家信息管理）。
 *
 * 档案由登录态定位，不需要（也不允许）传 expertId —— 传了就等于让任何专家
 * 都能按 id 翻别人的档案。
 *
 * @returns {Promise<Object>} ExpertProfileVO：
 *   { expertId, userId, realName, avatarUrl, expertDomain, introduction,
 *     workloadStat, auditStatus, createTime }
 *   比 getExperts() 多 auditStatus / createTime，供本人了解过审情况；
 *   不含 phone（手机号走 GET /user/profile）。
 */
export function getExpertProfile() {
  return request.get('/expert/profile')
}

/**
 * 个人中心：专家编辑本人档案。
 *
 * 请求体只有两个字段，别试图加别的：
 *   { expertDomain?, introduction? }
 * `workloadStat` 由系统统计、`auditStatus` 由管理员审核，后端 DTO 里根本没有
 * 这两个字段，传了也不生效。
 *
 * 两个字段**全空**时后端返回 400「请至少填写一项要修改的内容」（避免假成功），
 * 所以调用方在提交前应当先判断是否有实际改动。
 *
 * 字段语义差异（踩过就别再踩）：
 *   expertDomain 传空串 = **跳过**（不清空已有领域），要改必须给非空值；
 *   introduction 传空串 = **清空简介**，传 undefined = 不改。
 */
export function updateExpertProfile(payload) {
  return request.put('/expert/profile', payload)
}
