package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.vo.AdminUserVO;
import com.rxt.vo.RoleVO;

import java.util.List;

/**
 * 用户管理业务接口（M6 平台治理 / REQ-14 角色与权限管理）。
 *
 * <p>实现类位于 com.rxt.service.impl。本模块只对平台管理员开放，
 * 角色校验在实现类进入业务逻辑前完成，非管理员一律 403。</p>
 *
 * <p>操作人身份一律由 Controller 从登录态传入，不接受前端自报的 operatorId。</p>
 */
public interface AdminUserService {

    /**
     * 用户分页列表。
     *
     * @param roleId   按角色筛选，null 表示不限
     * @param keyword  按姓名或手机号模糊匹配，空表示不限
     * @param status   按账号状态筛选（1 正常 / 0 停用），null 表示不限
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     */
    IPage<AdminUserVO> pageUsers(Long roleId, String keyword, Integer status, long pageNum, long pageSize);

    /**
     * 启用或停用账号。
     * 停用后该账号无法登录（登录时按 t_user.status 拦截），已签发的令牌到期即失效。
     */
    void updateStatus(Long operatorId, Long userId, Integer status);

    /**
     * 调整用户所属角色。
     */
    void updateRole(Long operatorId, Long userId, Long roleId);

    /**
     * 角色下拉列表，供筛选与调整角色时使用。
     */
    List<RoleVO> listRoles();
}
