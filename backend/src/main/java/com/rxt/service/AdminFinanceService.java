package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.vo.FinanceApplyVO;

/**
 * 融资台账业务接口（M6 平台治理 · 管理后台「融资管理」页）。
 *
 * <p>与 {@link LoanAdminService} 的分工：银行用户负责审批，平台管理员只读台账。
 * 概要设计 2.2.1 明确管理员「不参与融资业务闭环」，因此本接口不提供任何写操作 ——
 * 管理员能看到全部申请与审批结果，但不能替银行出结论。</p>
 *
 * <p>实现类位于 com.rxt.service.impl，角色校验在实现类内完成，非管理员一律 403。</p>
 */
public interface AdminFinanceService {

    /**
     * 融资申请分页台账。
     *
     * @param applyStatus 按申请状态筛选，空表示不限
     * @param keyword     按申请编号或农户姓名模糊匹配，空表示不限
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页条数
     */
    IPage<FinanceApplyVO> pageApplies(String applyStatus, String keyword, long pageNum, long pageSize);
}
