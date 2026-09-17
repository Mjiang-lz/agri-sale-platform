package com.rxt.security;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import org.springframework.stereotype.Component;

/**
 * 角色权限校验。在 Service 层调用，用于限制越权的业务操作。
 */
@Component
public class PermissionChecker {

    /**
     * 要求当前用户具备指定角色之一。
     *
     * @param roles 允许的角色名称
     */
    public void requireRole(String... roles) {
        String current = UserContext.currentRole();
        if (current == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        for (String role : roles) {
            if (role.equals(current)) {
                return;
            }
        }
        throw new BizException(ResultCode.FORBIDDEN);
    }

    /**
     * 仅管理员可操作。
     */
    public void requireAdmin() {
        requireRole(RoleConstant.ADMIN);
    }
}
