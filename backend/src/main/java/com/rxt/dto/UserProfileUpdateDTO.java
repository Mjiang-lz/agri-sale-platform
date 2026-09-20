package com.rxt.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人基本资料修改入参。
 *
 * <p>只开放昵称与头像两项：</p>
 * <ul>
 *   <li>手机号是登录账号，变更需短信验证，本接口不提供（短信通道当前为模拟）</li>
 *   <li>角色与账号状态属平台管理范畴，用户不可自行修改</li>
 * </ul>
 *
 * <p>字段为 null 表示「本次不修改」，因此两个字段都可以不传。</p>
 */
@Data
public class UserProfileUpdateDTO {

    /** 昵称（真实姓名） */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String realName;

    /** 头像地址（对象存储 URL；OBS 未启用阶段可先手工填写） */
    @Size(max = 255, message = "头像地址长度不能超过 255 个字符")
    private String avatarUrl;
}
