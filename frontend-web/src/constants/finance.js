/**
 * 融资申请状态口径 —— 前端侧的唯一定义处。
 *
 * ★ 为什么要有这个文件（2026-10-10 补）
 *
 * 后端 `FinanceApplyServiceImpl#bankApprove` 里的写法是：
 *     apply.setApplyStatus("通过".equals(approvalResult) ? "已放款" : "审批未通过");
 * 也就是说 —— **银行审批通过时写入的状态是「已放款」，而不是「审批通过」**。
 * 「审批通过」这个字面量在 `AdminDashboardServiceImpl` 的状态全集里存在，
 * 但当前没有任何一条代码路径会写入它（库里能见到的只是早期演示数据）。
 *
 * 结果就是前端按 `applyStatus === '审批通过'` 做统计和筛选时，
 * **银行自己刚批过的申请一条都匹配不上** —— 用户点「已通过」tab 看到空白，
 * 会以为审批没生效。所以凡是判断「是否通过」的地方，
 * 一律走下面的 `isApprovedApply()`，「已放款」必须算作通过。
 *
 * ⚠️ 另一个更隐蔽的坑：不要用 `/通过/` 这类正则去判断。
 *   `'审批未通过'.match(/通过/)` 是**匹配成功的**（字面量里含「通过」二字），
 *   会把未通过的单子算进「已通过」，报出一个虚高的数字。
 *   必须用白名单精确匹配。
 */

/** 与后端 LoanAdminServiceImpl / FinanceApplyServiceImpl 中的字面量保持一致 */
export const APPLY_STATUS = {
  /** 已推送银行：银行侧待办，唯一可审批的状态 */
  PUSHED: '已推送银行',
  /** 审批通过（后端当前不写入此状态，保留以兼容演示数据与未来的独立放款动作） */
  APPROVED: '审批通过',
  /** 审批未通过（银行侧终态） */
  REJECTED: '审批未通过',
  /** 已放款 —— 银行审批「通过」后实际落入的状态 */
  LOANED: '已放款'
}

/** 「已通过」类状态集合。判断通过与否请用 isApprovedApply()，不要自己写条件。 */
export const APPROVED_APPLY_STATUSES = [APPLY_STATUS.APPROVED, APPLY_STATUS.LOANED]

/**
 * 一笔融资申请是否已通过（含已放款）。
 * @param {string} status 后端返回的 applyStatus 原值
 */
export function isApprovedApply(status) {
  return APPROVED_APPLY_STATUSES.includes(status)
}

/** 是否仍待银行审批（决定「审批」按钮是否出现） */
export function isPendingApply(status) {
  return status === APPLY_STATUS.PUSHED
}
