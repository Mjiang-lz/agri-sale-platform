package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.common.BizException;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.CaptchaService;
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
    private final CaptchaService captchaService;

    /**
     * 校验图形验证码。三个入口（登录/注册）共用，失败信息统一。
     *
     * <p>放在查库**之前**：验证码是防自动化撞库的第一道闸，先于密码比对执行，
     * 脚本拿不到验证码时连「手机号或密码错误」都探不到，也就无法用它遍历账号。</p>
     */
    private void requireCaptcha(String key, String code) {
        if (!captchaService.validate(key, code)) {
            throw new BizException("验证码不正确或已过期，请点击图片刷新后重试");
        }
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        requireCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

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
        // 头像随登录一起下发。漏掉它的话，前端 saveLogin 用登录响应覆盖本地 profile 时
        // 会把刚保存的头像地址冲成 undefined，顶栏（读 profile.avatarUrl）就永远显示首字。
        vo.setAvatarUrl(user.getAvatarUrl());

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
        requireCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

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
