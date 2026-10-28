package com.rxt.service;

import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.vo.LoginVO;

/**
 * 用户与认证模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface AuthService {

    /**
     * 登录，校验手机号与密码后签发令牌。
     *
     * <p>业务端入口，需要图形验证码（由实现内部校验）。</p>
     */
    LoginVO login(LoginDTO dto);

    /**
     * 管理后台登录：免图形验证码，且只允许管理员账号。
     *
     * <p>管理后台的登录页没有验证码输入框，但它与业务端曾共用 {@code login}，
     * 于是每次登录都被「验证码不正确或已过期」挡下。这里单独开入口，
     * 业务端的验证码规则不受影响。</p>
     */
    LoginVO adminLogin(LoginDTO dto);

    /**
     * 注册新用户。
     */
    void register(RegisterDTO dto);

}
