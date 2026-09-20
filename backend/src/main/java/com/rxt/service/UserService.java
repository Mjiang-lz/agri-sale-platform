package com.rxt.service;

import com.rxt.dto.FarmerProfileUpdateDTO;
import com.rxt.dto.UserProfileUpdateDTO;
import com.rxt.vo.UserVO;

/**
 * 个人信息模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》2.8 个人信息。</p>
 */
public interface UserService {

    /**
     * 查询个人资料。农户角色附带经营档案。
     */
    UserVO getProfile(Long userId);

    /**
     * 更新个人基本资料（昵称、头像）。
     * 只更新 DTO 中非 null 的字段。
     */
    void updateProfile(Long userId, UserProfileUpdateDTO dto);

    /**
     * 维护农户经营档案：不存在则建档，存在则部分更新。
     * 仅农户角色可调用，其余角色抛 403。
     */
    void updateFarmerProfile(Long userId, FarmerProfileUpdateDTO dto);

}
