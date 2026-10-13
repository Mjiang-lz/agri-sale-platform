package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 专家资格认证申请（《软件需求规格说明书》「专家入驻 → 资格认证」）。
 *
 * <p>自助注册时只写 {@code t_user}，专家档案要单独提交这份材料再由管理员审批 ——
 * 「能注册成专家」与「能作为专家开展服务」是两件事，前者只是拿到账号，
 * 后者需要管理员核对资质。</p>
 */
@Data
public class ExpertCertifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 擅长领域，如「种植与植保」。专家页与筛选都按它归类，必填 */
    @NotBlank(message = "请填写擅长领域")
    @Size(max = 50, message = "擅长领域不超过 50 字")
    private String expertDomain;

    /** 执业/工作经历与服务范围简介，专家介绍页的主体内容，必填 */
    @NotBlank(message = "请填写个人简介")
    @Size(max = 1000, message = "个人简介不超过 1000 字")
    private String introduction;
}
