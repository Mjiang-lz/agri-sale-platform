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

/* ====================================================================
 * 融资台账（管理员只读）
 *
 * 与上面 /finance/* 的区别有两处，都要看清：
 *   1) **数据范围**：/finance/apply/my 只返回本人提交，且后端 requireRole(FARMER)；
 *      台账返回全平台全部申请，后端是 requireAdmin() 放行的只读查询。
 *   2) **基础路径不同**：这里在 /admin/finance 下，不是 /finance 的子路径。
 *
 * 审批动作不在这条链路上（那属于 /loan-admin，只对银行用户开放）。
 * 管理员在这里能看见全部申请与银行结论，但出不了结论 —— 与平台治理的定位一致。
 * ==================================================================== */

/**
 * 融资申请分页台账。
 *
 * @param {object} [params]
 * @param {string} [params.applyStatus] 按申请状态筛选
 * @param {string} [params.keyword]     按申请编号或农户姓名模糊匹配
 * @returns 分页对象 { records, total, current, size }
 */
export function getFinanceLedger({ applyStatus, keyword, pageNum = 1, pageSize = 10 } = {}) {
  return request.get('/admin/finance/applies', {
    params: { applyStatus, keyword, pageNum, pageSize }
  })
}

/* ====================================================================
 * 融资管理（银行侧）
 *
 * 后端是独立的 LoanAdminController，基础路径 /api/loan-admin ——
 * 注意不是 /api/finance 下面的子路径，两者是并列的模块：
 *   · /finance/*     申请人视角（农户提交、农户看自己的进度）
 *   · /loan-admin/*  银行审批视角（看别人提交的、给出结论）
 * 分成两个模块是有意的：银行用户「看自己的申请」永远是空的，
 * 与农户共用一套接口会让权限判断和返回字段都变得含糊。
 *
 * 本组接口只对「银行用户」角色开放，其他角色调用会拿到
 * HTTP 200 + body code 403（后端在 Service 层 requireRole(BANK)）。
 * ==================================================================== */

/**
 * 查询已推送至银行侧的融资申请。
 *
 * ⚠️ 返回的是**数组**，不是 MyBatis-Plus 的分页对象 ——
 * 银行侧待办天然量少，后端没做分页，前端不要按 records/total 去读。
 *
 * @param {string} [status] 按申请状态筛选。不传即返回全部已推送的申请
 *        （含已出结论的）。注意后端筛选前会先按 push_time 非空过滤，
 *        所以「待匹配 / 已组建小组」这些还没推给银行的申请永远不出现。
 */
export function getBankApplies(status) {
  return request.get('/loan-admin/applies', {
    params: status ? { status } : {}
  })
}

/**
 * 提交审批结论。请求体对应 LoanApproveDTO：
 *   { approvalResult: '通过' | '不通过', loanAmount?, approvalOpinion? }
 *
 * 「通过时放款金额必填、不通过时审批意见必填」这两条由后端校验，
 * 前端做表单校验只是为了提前给反馈，不是安全边界 ——
 * 放款金额还额外限制「不得超过申请额度」，那条只在后端。
 *
 * ⚠️ 同一笔申请只能审批一次：后端只允许对状态仍为「已推送银行」的申请出结论，
 * 重复提交会报「该申请当前状态为「审批通过」，不可审批」。
 * 所以提交成功后必须重新拉列表，不能只改本地那一行。
 */
export function approveBankApply(applyId, payload) {
  return request.post(`/loan-admin/applies/${applyId}/approve`, payload)
}
