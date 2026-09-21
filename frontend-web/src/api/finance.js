import request from './request'

/** 融资申请页：查询在售贷款产品。后端返回 MyBatis-Plus Page。 */
export function getLoanProducts(page = 1, size = 100) {
  return request.get('/finance/loan-products', {
    params: { page, size }
  })
}

export function submitFinanceApply(payload) {
  return request.post('/finance/apply', payload)
}

export function getRecommendedGuarantors() {
  return request.get('/finance/recommend')
}

export function createLoanGroup(payload) {
  return request.post('/finance/group', payload)
}

/** 我的融资页：查询当前登录用户自己的融资申请列表。 */
export function getMyFinanceApplies() {
  return request.get('/finance/apply/my')
}