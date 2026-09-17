package com.rxt.service.impl;

import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.service.AuthService;
import com.rxt.vo.LoginVO;
import org.springframework.stereotype.Service;

/**
 * 用户与认证模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public LoginVO login(LoginDTO dto) {
        // TODO 待实现：从数据库装配视图对象
        return null;
    }

    @Override
    public void register(RegisterDTO dto) {
        // TODO 待实现
    }

}
