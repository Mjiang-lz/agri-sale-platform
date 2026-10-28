import request from './request'

/**
 * M6 融资管理接口（管理后台 · 融资管理页）。
 *
 * 管理员视角的<b>只读台账</b>：看得见全部融资申请与银行侧审批结果，但不出结论。
 * 审批动作在 /api/loan-admin，只对银行用户开放；管理员调用会返回 403。
 *
 * - 分页台账：GET /api/admin/finance/applies
 * - 状态分布（用于漏斗图）：GET /api/admin/dashboard/statistics 的 financeStatusDistribution
 */

/** 融资申请分页台账。applyStatus 按状态筛选，keyword 按申请编号或农户姓名模糊匹配。 */
export function getAdminFinanceApplies({ applyStatus, keyword, pageNum = 1, pageSize = 10 } = {}) {
  return request.get('/admin/finance/applies', {
    params: { applyStatus, keyword, pageNum, pageSize }
  })
}

/**
 * 融资状态分布。
 *
 * 台账接口是分页的，只看当前页算不出整体分布；这里复用数据看板接口里已有的
 * financeStatusDistribution（后端一次性按 6 个状态词表统计，数量为 0 也保留），
 * 避免为了一张漏斗图再写一个后端接口。
 */
export function getFinanceStatusDistribution() {
  return request.get('/admin/dashboard/statistics')
}
