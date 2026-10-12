package com.rxt.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 专家维护本人资料的入参。
 *
 * <p>对应《软件需求规格说明书》「个人信息 → 专家信息管理」：
 * 输入为「擅长领域、简介、服务方式、头像」，处理为「编辑并保存」。</p>
 *
 * <p><b>刻意不包含的字段</b>（传了也不会生效，因为这里根本没有对应属性）：</p>
 * <ul>
 *   <li>{@code workloadStat} —— 系统统计口径（由问答数、预约数计算），不是用户输入；</li>
 *   <li>{@code auditStatus} —— 审核结论。专家若能改自己的审核状态，等于自行批准自己；</li>
 *   <li>{@code userId} / {@code expertId} —— 档案与账号是一对一（{@code uk_expert_user_id}），
 *       不允许改挂；归属一律取自登录态。</li>
 * </ul>
 *
 * <p><b>关于「服务方式」</b>：SRS 把它列为输入字段之一，但该字段在《详细设计说明书》、
 * {@code schema.sql} 与代码中均不存在（检索 {@code 服务方式} / {@code service_mode} 零命中）。
 * 本次不新增列，改由 {@code introduction} 承载 ——
 * {@code t_expert.introduction} 的 DDL 注释本就是「专家介绍与<b>服务范围</b>」，语义已覆盖。</p>
 *
 * <p><b>头像不在此接口</b>：头像落在 {@code t_user.avatar_url}，走已有的
 * {@code PUT /api/user/profile}，避免同一个字段有两条写入路径。</p>
 */
@Data
public class ExpertProfileUpdateDTO {

    /** 专业领域。上限 50 对齐 t_expert.expert_domain varchar(50)。 */
    @Size(max = 50, message = "擅长领域不能超过 50 个字")
    private String expertDomain;

    /**
     * 简介与服务范围（承载 SRS 的「服务方式」）。
     *
     * <p>不设长度上限：DDL 是 {@code TEXT}，与同域的 {@code Knowledge.content} 口径一致。
     * 凭空加一个上游没有的字符数限制，会让合法内容在接口层被拒而 DDL 明明放得下。</p>
     */
    private String introduction;
}
