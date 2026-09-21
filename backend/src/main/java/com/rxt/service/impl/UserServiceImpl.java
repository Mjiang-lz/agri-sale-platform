package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.FarmerProfileUpdateDTO;
import com.rxt.dto.UserProfileUpdateDTO;
import com.rxt.entity.FarmerProfile;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.UserService;
import com.rxt.vo.FarmerProfileVO;
import com.rxt.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 个人信息模块业务实现。
 *
 * <p>用户主键一律由 Controller 从登录态传入，本层不接受前端自报的 userId。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final FarmerProfileMapper farmerProfileMapper;
    private final PermissionChecker permissionChecker;

    @Override
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }

        // 装配基础信息 —— 注意始终不返回 password
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setStatus(user.getStatus());
        // 角色从库里取而不是抄登录态：管理员调整角色后能立即反映
        Role role = user.getRoleId() == null ? null : roleMapper.selectById(user.getRoleId());
        if (role != null) {
            vo.setRoleId(role.getRoleId());
            vo.setRoleName(role.getRoleName());
            vo.setMenuPerms(role.getMenuPerms());
        }

        // 经营档案仅农户有
        if (RoleConstant.FARMER.equals(vo.getRoleName())) {
            vo.setFarmerProfile(loadFarmerProfile(userId));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }

        // 逐字段部分更新：null 表示「本次不改」，传空串才是清空
        boolean changed = false;
        if (dto.getRealName() != null) {
            if (!StringUtils.hasText(dto.getRealName())) {
                throw new BizException("昵称不能为空");
            }
            user.setRealName(dto.getRealName().trim());
            changed = true;
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl().trim());
            changed = true;
        }
        if (!changed) {
            throw new BizException("没有需要修改的内容");
        }
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFarmerProfile(Long userId, FarmerProfileUpdateDTO dto) {
        // 经营档案只属于农户，其余角色一律拒绝（403）
        permissionChecker.requireRole(RoleConstant.FARMER);

        FarmerProfile exist = findFarmerProfile(userId);

        // 首次维护则建档（一人一档，由 uk_farmer_profile_user_id 兜底保证唯一）
        if (exist == null) {
            FarmerProfile profile = new FarmerProfile();
            profile.setUserId(userId);
            profile.setMainProduct(dto.getMainProduct());
            profile.setScaleDesc(dto.getScaleDesc());
            profile.setCreditRecord(dto.getCreditRecord());
            farmerProfileMapper.insert(profile);
            return;
        }

        boolean changed = false;
        if (dto.getMainProduct() != null) {
            exist.setMainProduct(dto.getMainProduct().trim());
            changed = true;
        }
        if (dto.getScaleDesc() != null) {
            exist.setScaleDesc(dto.getScaleDesc().trim());
            changed = true;
        }
        if (dto.getCreditRecord() != null) {
            exist.setCreditRecord(dto.getCreditRecord().trim());
            changed = true;
        }
        if (!changed) {
            throw new BizException("没有需要修改的内容");
        }
        farmerProfileMapper.updateById(exist);
    }

    /**
     * 按用户加载经营档案，一个用户至多一条。
     */
    private FarmerProfile findFarmerProfile(Long userId) {
        LambdaQueryWrapper<FarmerProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FarmerProfile::getUserId, userId);
        return farmerProfileMapper.selectOne(wrapper);
    }

    /**
     * 档案实体转视图对象。
     */
    private FarmerProfileVO loadFarmerProfile(Long userId) {
        FarmerProfile profile = findFarmerProfile(userId);
        if (profile == null) {
            return null;
        }
        FarmerProfileVO vo = new FarmerProfileVO();
        vo.setProfileId(profile.getProfileId());
        vo.setMainProduct(profile.getMainProduct());
        vo.setScaleDesc(profile.getScaleDesc());
        vo.setCreditRecord(profile.getCreditRecord());
        return vo;
    }
}
