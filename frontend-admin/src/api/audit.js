import request from './request'

/**
 * M6 内容审核公共接口。
 *
 * 商品审核页当前只使用 PRODUCT，但接口封装保持通用，后续知识、评论审核页可以直接复用。
 *
 * - 待审列表：GET /api/audit/pending
 * - 审核列表：GET /api/audit/list
 * - 内容详情：GET /api/audit/content/{targetType}/{targetId}
 * - 提交结论：POST /api/audit/content/{targetType}/{targetId}
 * - 审核统计：GET /api/audit/statistics
 */

/** 商品审核页：查询待审核商品。 */
export function getProductPendingList(pageNum = 1, pageSize = 10) {
  return request.get('/audit/pending', {
    params: { targetType: 'PRODUCT', pageNum, pageSize }
  })
}

/** 商品审核页：按审核状态查询商品审核记录。 */
export function getProductAuditList(auditStatus, pageNum = 1, pageSize = 10) {
  return request.get('/audit/list', {
    params: { targetType: 'PRODUCT', auditStatus, pageNum, pageSize }
  })
}

/** 商品审核页：查看商品完整描述。 */
export function getProductAuditContent(targetId) {
  return request.get(`/audit/content/PRODUCT/${targetId}`)
}

/** 商品审核页：提交通过/驳回结论。 */
export function submitProductAudit(targetId, payload) {
  return request.post(`/audit/content/PRODUCT/${targetId}`, payload)
}

/** 商品审核页：查询我的累计审核数和近 7 日审核趋势。 */
export function getProductAuditStatistics() {
  return request.get('/audit/statistics', {
    params: { targetType: 'PRODUCT' }
  })
}

/** 知识审核页：查询待审核知识。 */
export function getKnowledgePendingList(pageNum = 1, pageSize = 10) {
  return request.get('/audit/pending', {
    params: { targetType: 'KNOWLEDGE', pageNum, pageSize }
  })
}

/** 知识审核页：按审核状态查询知识审核记录。 */
export function getKnowledgeAuditList(auditStatus, pageNum = 1, pageSize = 10) {
  return request.get('/audit/list', {
    params: { targetType: 'KNOWLEDGE', auditStatus, pageNum, pageSize }
  })
}

/** 知识审核页：查看知识正文。 */
export function getKnowledgeAuditContent(targetId) {
  return request.get(`/audit/content/KNOWLEDGE/${targetId}`)
}

/** 知识审核页：提交通过/驳回结论。 */
export function submitKnowledgeAudit(targetId, payload) {
  return request.post(`/audit/content/KNOWLEDGE/${targetId}`, payload)
}

/** 知识审核页：查询我的累计审核数和近 7 日审核趋势。 */
export function getKnowledgeAuditStatistics() {
  return request.get('/audit/statistics', {
    params: { targetType: 'KNOWLEDGE' }
  })
}

/** 评论审核页：查询待审核评论。 */
export function getCommentPendingList(pageNum = 1, pageSize = 10) {
  return request.get('/audit/pending', {
    params: { targetType: 'COMMENT', pageNum, pageSize }
  })
}

/** 评论审核页：按审核状态查询评论审核记录。 */
export function getCommentAuditList(auditStatus, pageNum = 1, pageSize = 10) {
  return request.get('/audit/list', {
    params: { targetType: 'COMMENT', auditStatus, pageNum, pageSize }
  })
}

/** 评论审核页：查看评论全文。 */
export function getCommentAuditContent(targetId) {
  return request.get(`/audit/content/COMMENT/${targetId}`)
}

/** 评论审核页：提交通过/驳回结论。 */
export function submitCommentAudit(targetId, payload) {
  return request.post(`/audit/content/COMMENT/${targetId}`, payload)
}

/** 评论审核页：查询我的累计审核数和近 7 日审核趋势。 */
export function getCommentAuditStatistics() {
  return request.get('/audit/statistics', {
    params: { targetType: 'COMMENT' }
  })
}
