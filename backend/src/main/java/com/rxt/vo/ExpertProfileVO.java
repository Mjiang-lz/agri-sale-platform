package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 专家档案视图对象 —— 供<b>本人</b>与<b>管理员</b>两侧共用。
 *
 * <p>为什么另立一个 VO 而不复用 {@link ExpertVO}：{@code ExpertVO} 是给公开的「专家一览」
 * 用的，那边只查已通过审核的档案，因此不带审核状态；而本人要看自己档案过没过审、
 * 管理员要按审核状态筛选，所以这里多一个 {@code auditStatus}。两者字段集不同、
 * 用途不同，硬合并会让公开接口带出一列它永远只是「已通过」的噪音。</p>
 *
 * <p>同样<b>刻意不返回 {@code phone}</b>：手机号是登录凭据，档案维护不需要它
 * （与 {@link ExpertVO} 同一口径）。</p>
 */
@Data
public class ExpertProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 专家主键 */
    private Long expertId;

    /** 关联的用户主键 */
    private Long userId;

    /** 专家姓名（t_user.real_name） */
    private String realName;

    /** 头像地址（t_user.avatar_url）；为空时前端显示姓名首字 */
    private String avatarUrl;

    /** 专业领域 */
    private String expertDomain;

    /** 简介与服务范围（承载 SRS 的「服务方式」） */
    private String introduction;

    /** 服务工作量统计。只读：由系统计算，专家不可编辑 */
    private String workloadStat;

    /** 审核状态：待审核、已通过、未通过。只读：专家不可编辑，管理员可改 */
    private String auditStatus;

    /** 建档时间 */
    private LocalDateTime createTime;
}
