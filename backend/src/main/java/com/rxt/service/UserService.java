package com.rxt.service;

import com.rxt.vo.UserVO;

/**
 * 个人信息模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface UserService {

    /**
     * 查询个人资料。
     */
    UserVO getProfile(Long userId);

    /**
     * 更新个人资料。
     */
    void updateProfile(UserVO vo);

}
