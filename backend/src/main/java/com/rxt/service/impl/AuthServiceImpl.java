package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.entity.ExpertInfo;
import com.rxt.mapper.ExpertInfoMapper;
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

    /** 专家档案审核状态：待审核（刚自助注册、资质材料还没交/还没审） */
    private static final String AUDIT_PENDING = "待审核";

    /** BCrypt 加盐哈希，线程安全，可当单例复用 */
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ExpertInfoMapper expertInfoMapper;
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
        return authenticate(dto);
    }

    /**
     * 管理后台登录：<b>不校验图形验证码</b>，但只接受管理员账号。
     *
     * <p>为什么单独开一个入口而不是在 {@link #login(LoginDTO)} 里跳过验证码：
     * 管理后台与业务端共用 {@code POST /auth/login}，而业务端（农户 / 采购方 / 专家 / 银行）
     * 的登录页仍保留验证码。若在这里按「有没有传验证码」来决定校不校验，
     * 等于把开关交到调用方手上 —— 攻击者只要不传验证码就能绕过，验证码形同虚设。
     * 拆成两个入口后，「业务端必须过验证码、后台不过」是<b>服务端写死的规则</b>，
     * 且后台入口顺带把角色闸口收紧到管理员，非管理员连令牌都拿不到。</p>
     */
    @Override
    public LoginVO adminLogin(LoginDTO dto) {
        LoginVO vo = authenticate(dto);
        if (!RoleConstant.ADMIN.equals(vo.getRoleName())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "该账号不是平台管理员，无法进入管理后台");
        }
        log.info("管理后台登录成功 userId={} phone={}", vo.getUserId(), vo.getPhone());
        return vo;
    }

    /**
     * 手机号 + 密码校验并签发令牌。<b>不含验证码环节</b> —— 验证码属于「入口策略」，
     * 由 {@link #login(LoginDTO)} / {@link #adminLogin(LoginDTO)} 各自决定要不要。
     */
    private LoginVO authenticate(LoginDTO dto) {
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

        // ★ 选「专家」必须同时建一份待审核的专家档案（需求 10）。
        //   改前只 insert t_user，于是这个账号：不在任何专家列表里
        //   （listExperts 按 audit_status='已通过' 过滤），
        //   requireRole(EXPERT) 也查不到档案 —— 「注册成了专家，却什么都不是」。
        //   档案状态给「待审核」，管理员在「专家资源管理」里审批通过后才对外可见。
        if (RoleConstant.EXPERT.equals(role.getRoleName())) {
            ExpertInfo expert = new ExpertInfo();
            expert.setUserId(user.getUserId());
            // 领域与简介留给本人登录后通过 POST /expert/certify 补交，
            // 注册页只收手机号/密码/姓名，问专业资质要再填一整套表单才能提交
            expert.setExpertDomain(null);
            expert.setIntroduction(null);
            expert.setAuditStatus(AUDIT_PENDING);
            expertInfoMapper.insert(expert);
            log.info("已创建待审核专家档案 expertId={} userId={}",
                    expert.getExpertId(), user.getUserId());
        }

        log.info("注册成功 userId={} phone={} role={}", user.getUserId(), user.getPhone(), role.getRoleName());
    }

}
