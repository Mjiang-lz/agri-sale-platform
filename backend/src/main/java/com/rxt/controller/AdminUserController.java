package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.UserRoleUpdateDTO;
import com.rxt.dto.UserStatusUpdateDTO;
import com.rxt.security.UserContext;
import com.rxt.service.AdminUserService;
import com.rxt.vo.AdminUserVO;
import com.rxt.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口。基础路径 /api/admin。
 *
 * <p>与 {@link UserController}（/api/user，个人中心）的区别：本接口是<b>治理功能</b>，
 * 面向管理员跨用户操作；个人中心只能读写自己那一份数据。
 * 两者都在 WebMvcConfig 的鉴权范围内，且本接口的角色校验（仅管理员）
 * 在 Service 层由 {@code PermissionChecker} 完成。</p>
 *
 * <p>操作人身份一律取自登录态 {@link UserContext}，不接受客户端传入 ——
 * 用请求参数传操作人，任何登录用户都能以管理员名义停用他人账号。</p>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 用户分页列表。支持按角色、姓名/手机号关键字、账号状态筛选。
     */
    @GetMapping("/users")
    public Result<IPage<AdminUserVO>> pageUsers(@RequestParam(required = false) Long roleId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminUserService.pageUsers(roleId, keyword, status, pageNum, pageSize));
    }

    /**
     * 启用 / 停用账号。
     */
    @PutMapping("/users/{userId}/status")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @RequestBody @Valid UserStatusUpdateDTO dto) {
        adminUserService.updateStatus(UserContext.currentUserId(), userId, dto.getStatus());
        return Result.success();
    }

    /**
     * 调整用户角色。
     */
    @PutMapping("/users/{userId}/role")
    public Result<Void> updateRole(@PathVariable Long userId,
                                   @RequestBody @Valid UserRoleUpdateDTO dto) {
        adminUserService.updateRole(UserContext.currentUserId(), userId, dto.getRoleId());
        return Result.success();
    }

    /**
     * 角色列表，供筛选条件与「调整角色」弹窗的下拉项使用。
     */
    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.success(adminUserService.listRoles());
    }
}
