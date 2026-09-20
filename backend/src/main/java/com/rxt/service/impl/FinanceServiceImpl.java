package com.rxt.service.impl;

import com.rxt.dto.FinanceApplyDTO;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.FinanceService;
import com.rxt.vo.FinanceApplyVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 融资服务模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 *
 * <p>融资申请是农户独有的动作（需求规格说明书 2.5 融资服务），
 * 因此进入业务逻辑前先做角色校验，非农户一律 403。</p>
 */
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final PermissionChecker permissionChecker;

    @Override
    public Long submitApply(Long farmerId, FinanceApplyDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        // TODO 待实现：需返回真实主键 / 计数
        return null;
    }

    @Override
    public List<FinanceApplyVO> listMyApplies(Long farmerId) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

}
