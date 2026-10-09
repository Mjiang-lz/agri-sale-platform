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

/** 融资申请页：获取系统推荐的联保候选人（REQ-07，真实现，非演示数据）。 */
export function getRecommendedGuarantors() {
  return request.get('/finance/recommend')
}

/**
 * 融资申请页：创建联合贷款小组。
 *
 * 请求体对应 LoanGroupDTO：{ groupName?, memberIds?, loanProductId? }。
 * 返回值对应 LoanGroupCreateVO：
 * { groupId, groupName, leaderId, memberCount, minGroupSize, targetAmount, groupStatus,
 *   joined: [{ userId, realName, applyAmount, memberRole }],
 *   skipped: [{ userId, realName, reason }] }
 *
 * 注意 skipped 不是异常：被推荐人可能因为「已在其他进行中的小组」
 * 或「还没提交同产品的融资申请」而没能入组，必须把原因展示给用户，
 * 否则发起人会以为小组已经凑齐。
 */
export function createLoanGroup(payload) {
  return request.post('/finance/group', payload)
}

/** 我的融资页：分页查询当前登录用户自己的融资申请。 */
export function getMyFinanceApplies(pageNum = 1, pageSize = 100) {
  return request.get('/finance/apply/my', {
    params: { pageNum, pageSize }
  })
}
