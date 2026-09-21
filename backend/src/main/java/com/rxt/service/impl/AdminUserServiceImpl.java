package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.AdminUserService;
import com.rxt.vo.AdminUserVO;
import com.rxt.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户管理业务实现（M6 平台治理 / REQ-14）。
 *
 * <p>治理动作的红线：</p>
 * <ul>
 *   <li>操作人取自登录态，且必须是管理员 —— 否则任何登录用户都能停用他人账号；</li>
 *   <li>不允许把系统改到「一个可用的管理员都不剩」，否则治理入口自锁，只能改库救回；</li>
 *   <li>本模块不物理删除用户。需求规格说明书 2.8 明确「所有用户管理」保留账号痕迹，
 *       业务数据（商品、订单、融资）都外键指向 t_user，删号会让这些数据变成孤儿。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** 账号状态：正常 */
    private static final int STATUS_ENABLED = 1;

    /** 账号状态：停用 */
    private static final int STATUS_DISABLED = 0;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionChecker permissionChecker;

    @Override
    public IPage<AdminUserVO> pageUsers(Long roleId, String keyword, Integer status,
                                        long pageNum, long pageSize) {
        permissionChecker.requireAdmin();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (roleId != null) {
            wrapper.eq(User::getRoleId, roleId);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 姓名与手机号任一命中即可，括号包住 or 条件，避免把上面的筛选条件一起放开
            wrapper.and(q -> q.like(User::getRealName, kw).or().like(User::getPhone, kw));
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getUserId);

        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 角色只有 5 条，一次性取出建映射，避免逐行查库造成 N+1
        Map<Long, String> roleNames = loadRoleNames();

        Page<AdminUserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(user -> toVO(user, roleNames))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorId, Long userId, Integer status) {
        permissionChecker.requireAdmin();

        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException("账号状态取值非法，只能是 1（正常）或 0（停用）");
        }
        User target = requireUser(userId);

        if (Objects.equals(operatorId, userId) && status == STATUS_DISABLED) {
            throw new BizException("不能停用当前登录的管理员账号");
        }
        if (status == STATUS_DISABLED) {
            // 停用管理员前先确认系统里还有别的可用管理员
            assertAdminRemains(target);
        }

        User update = new User();
        update.setUserId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        log.info("用户状态变更 operatorId={} userId={} status={}", operatorId, userId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long operatorId, Long userId, Long roleId) {
        permissionChecker.requireAdmin();

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("目标角色不存在");
        }
        User target = requireUser(userId);

        if (Objects.equals(target.getRoleId(), roleId)) {
            throw new BizException("该用户已经是「" + role.getRoleName() + "」角色");
        }
        // 把最后一个可用的管理员改成其他角色，同样会让治理入口自锁
        assertAdminRemains(target);

        User update = new User();
        update.setUserId(userId);
        update.setRoleId(roleId);
        userMapper.updateById(update);

        log.info("用户角色变更 operatorId={} userId={} roleId={}", operatorId, userId, roleId);
    }

    @Override
    public List<RoleVO> listRoles() {
        permissionChecker.requireAdmin();
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getRoleId))
                .stream()
                .map(role -> {
                    RoleVO vo = new RoleVO();
                    vo.setRoleId(role.getRoleId());
                    vo.setRoleName(role.getRoleName());
                    vo.setMenuPerms(role.getMenuPerms());
                    vo.setDescription(role.getDescription());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 取用户，不存在直接 404。
     */
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        return user;
    }

    /**
     * 校验：本操作之后，系统里至少还剩一名「启用状态的管理员」。
     *
     * <p>目标用户不是启用中的管理员时直接放行 —— 这条规则只保护治理入口本身。</p>
     */
    private void assertAdminRemains(User target) {
        Long adminRoleId = adminRoleId();
        if (adminRoleId == null || !adminRoleId.equals(target.getRoleId())) {
            return;
        }
        if (target.getStatus() == null || target.getStatus() != STATUS_ENABLED) {
            return;
        }
        Long enabledAdmins = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getRoleId, adminRoleId)
                .eq(User::getStatus, STATUS_ENABLED));
        if (enabledAdmins != null && enabledAdmins <= 1) {
            throw new BizException("系统至少需要保留一名启用的管理员账号");
        }
    }

    /**
     * 管理员角色主键；角色数据缺失时返回 null，由调用方按「无需保护」处理。
     */
    private Long adminRoleId() {
        Role admin = roleMapper.selectOne(
                Wrappers.<Role>lambdaQuery().eq(Role::getRoleName, RoleConstant.ADMIN));
        return admin == null ? null : admin.getRoleId();
    }

    /**
     * 角色主键 → 角色名映射。
     */
    private Map<Long, String> loadRoleNames() {
        return roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName, (a, b) -> a));
    }

    /**
     * 实体转列表行视图，注意不携带 password。
     */
    private AdminUserVO toVO(User user, Map<Long, String> roleNames) {
        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(user.getUserId());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRoleId(user.getRoleId());
        vo.setRoleName(roleNames.get(user.getRoleId()));
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
