package com.rxt.service.impl;

import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.LoanAdminService;
import com.rxt.vo.FinanceApplyVO;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 融资管理（银行侧）模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 *
 * <p>本模块整体只对「银行用户」开放（需求规格说明书 2.7 融资管理：智能匹配农户、贷款审批；
 * 平台管理员不参与业务闭环），因此每个方法进入业务逻辑前先做角色校验，非银行用户一律 403。</p>
 */
@Service
@RequiredArgsConstructor
public class LoanAdminServiceImpl implements LoanAdminService {

    private final PermissionChecker permissionChecker;

    @Override
    public List<FinanceApplyVO> listPushedApplies(String status) {
        permissionChecker.requireRole(RoleConstant.BANK);
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

    @Override
    public void approve(Long applyId, Long bankUserId, boolean pass, BigDecimal loanAmount, String opinion) {
        permissionChecker.requireRole(RoleConstant.BANK);
        // TODO 待实现
    }

}
