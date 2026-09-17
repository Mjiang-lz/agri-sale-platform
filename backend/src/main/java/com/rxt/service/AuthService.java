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
     */
    LoginVO login(LoginDTO dto);

    /**
     * 注册新用户。
     */
    void register(RegisterDTO dto);

}
