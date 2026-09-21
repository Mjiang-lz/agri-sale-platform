import request from './request'

/**
 * 融资申请页：查询在售贷款产品。
 * 接口：GET /api/finance/loan-products
 * 后端返回 MyBatis-Plus Page。
 */
export function getLoanProducts(page = 1, size = 100) {
  return request.get('/finance/loan-products', {
    params: { page, size }
  })
}

/** 融资申请页：提交融资申请，请求体对应 FinanceApplyDTO。 */
export function submitFinanceApply(payload) {
  return request.post('/finance/apply', payload)
}

/** 融资申请页：获取系统推荐的联保候选人。 */
export function getRecommendedGuarantors() {
  return request.get('/finance/recommend')
}

/** 融资申请页：创建联合贷款小组。 */
export function createLoanGroup(payload) {
  return request.post('/finance/group', payload)
}

/** 我的融资页：分页查询当前登录用户自己的融资申请。 */
export function getMyFinanceApplies(pageNum = 1, pageSize = 100) {
  return request.get('/finance/apply/my', {
    params: { pageNum, pageSize }
  })
}
