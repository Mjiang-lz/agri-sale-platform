package com.rxt.service.impl;

import com.rxt.service.UserService;
import com.rxt.vo.UserVO;
import org.springframework.stereotype.Service;

/**
 * 个人信息模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public UserVO getProfile(Long userId) {
        // TODO 待实现：从数据库装配视图对象
        return null;
    }

    @Override
    public void updateProfile(UserVO vo) {
        // TODO 待实现
    }

}
