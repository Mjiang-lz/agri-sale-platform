package com.rxt.service.impl;

import com.rxt.dto.LoanGroupDTO;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.LoanGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联合贷款小组业务实现。
 *
 * <p>联保小组只可能由农户组成（需求规格说明书 2.5 融资服务：联合贷款小组组建），
 * 因此三个动作进入业务逻辑前先做角色校验，非农户一律 403。</p>
 */
@Service
@RequiredArgsConstructor
public class LoanGroupServiceImpl implements LoanGroupService {

    private final PermissionChecker permissionChecker;

    @Override
    public void createGroup(Long userId, LoanGroupDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        // 1. 校验用户是否有资格建组
        // 2. 校验该农户是否已在其他进行中的小组（同时最多参与一个）
        // 3. 插入 t_loan_group 表
    }

    @Override
    @Transactional // ⚠️ 团队红线：涉及多表写入，必须加事务防并发超员！
    public void joinGroup(Long groupId, Long userId) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        // 1. 查小组当前人数，校验是否达到上限
        // 2. 校验用户是否已在其他组
        // 3. 插入组员记录
    }

    @Override
    public void quitGroup(Long groupId, Long userId) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        // 1. 校验用户是否在组内
        // 2. 删除组员记录
    }
}
