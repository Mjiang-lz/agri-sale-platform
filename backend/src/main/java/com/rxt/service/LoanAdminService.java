package com.rxt.service;

import com.rxt.vo.FinanceApplyVO;
import java.math.BigDecimal;
import java.util.List;

/**
 * 融资管理（银行侧）模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface LoanAdminService {

    /**
     * 查询已推送至银行侧的融资申请。
     */
    List<FinanceApplyVO> listPushedApplies(String status);

    /**
     * 审批融资申请。
     */
    void approve(Long applyId, Long bankUserId, boolean pass, BigDecimal loanAmount, String opinion);

}
