package com.rxt.service.impl;

import com.rxt.service.LoanAdminService;
import com.rxt.vo.FinanceApplyVO;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 融资管理（银行侧）模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class LoanAdminServiceImpl implements LoanAdminService {

    @Override
    public List<FinanceApplyVO> listPushedApplies(String status) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

    @Override
    public void approve(Long applyId, Long bankUserId, boolean pass, BigDecimal loanAmount, String opinion) {
        // TODO 待实现
    }

}
