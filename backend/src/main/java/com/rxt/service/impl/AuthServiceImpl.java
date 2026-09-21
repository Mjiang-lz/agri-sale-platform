package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.common.BizException;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.JwtUtils;
import com.rxt.security.RoleConstant;
import com.rxt.service.AuthService;
import com.rxt.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户与认证模块业务实现。
 *
 * <p>密码一律以 BCrypt 加盐哈希入库，库中不保存明文；登录成功后签发 JWT，
 * 令牌的解析与过期校验由 {@link com.rxt.security.LoginInterceptor} 负责，
 * 过期令牌会在那一层被拦下并返回 401。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * 允许自助注册的角色。管理员不在其中 —— 管理员账号只能由平台内部维护，
     * 一旦开放注册，任何人都能把自己注册成管理员。
     */
    private static final List<String> REGISTRABLE_ROLES = List.of(
            RoleConstant.FARMER,
            RoleConstant.BUYER,
            RoleConstant.EXPERT,
            RoleConstant.BANK);

    /** 账号状态：正常 */
    private static final int STATUS_ENABLED = 1;

    /** BCrypt 加盐哈希，线程安全，可当单例复用 */
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final JwtUtils jwtUtils;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, dto.getPhone()));

        // 账号不存在与密码错误返回同一句提示，避免被拿来探测某个手机号是否已注册
        if (user == null || !PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException("手机号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BizException("该账号已停用，请联系平台管理员");
        }

        Role role = roleMapper.selectById(user.getRoleId());
        if (role == null) {
            throw new BizException("账号角色数据异常，请联系平台管理员");
        }

        String token = jwtUtils.generateToken(user.getUserId(), user.getPhone(), role.getRoleName());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getUserId());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setRoleId(role.getRoleId());
        vo.setRoleName(role.getRoleName());
        // 菜单权限随登录响应一起下发，前端据此渲染侧边栏，
        // 实现「不同角色看到的菜单不同」；权限判定仍以后端服务层为准。
        vo.setMenuPerms(role.getMenuPerms());

        log.info("登录成功 userId={} phone={} role={}", user.getUserId(), user.getPhone(), role.getRoleName());
        return vo;
    }

    /**
     * 注册新用户。
     *
     * <p>用事务包住是给后续扩展留的口子：注册成功后若还要写入农户经营档案等关联数据，
     * 中途失败需要整笔回滚，那时不必再改这个方法的结构。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        if (!REGISTRABLE_ROLES.contains(dto.getRoleName())) {
            throw new BizException("该身份不支持自助注册");
        }

        // 先查一次是为了给出友好提示；表上的 uk_user_phone 唯一索引才是最终兜底
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, dto.getPhone()));
        if (exists != null && exists > 0) {
            throw new BizException("该手机号已注册，请直接登录");
        }

        Role role = roleMapper.selectOne(
                Wrappers.<Role>lambdaQuery().eq(Role::getRoleName, dto.getRoleName()));
        if (role == null) {
            throw new BizException("角色不存在：" + dto.getRoleName());
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRoleId(role.getRoleId());
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);

        log.info("注册成功 userId={} phone={} role={}", user.getUserId(), user.getPhone(), role.getRoleName());
    }

}
