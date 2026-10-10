package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 专家一览视图对象（概设 UI-EXP-03「专家介绍」，M4 专家助力）。
 *
 * <p>{@code t_expert} 里只有 {@code user_id}，姓名与头像落在 {@code t_user}。
 * 列表接口若只回主键，前端就得为每一行再查一次用户 —— 首页此前正是这么写的
 * （对每个专家打一次 {@code GET /user/{userId}/brief}），而那条路径在后端并不存在，
 * 结果是每次进首页都弹一条「接口不存在」。这里在后端一次装配好。</p>
 *
 * <p><b>刻意不返回 {@code phone}</b>：专家一览只需要展示身份，手机号是登录凭据，
 * 列表接口没有任何理由把它带出去（与 {@link GuarantorCandidateVO} 同一口径）。</p>
 */
@Data
public class ExpertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 专家主键 */
    private Long expertId;

    /** 关联的用户主键 */
    private Long userId;

    /** 专家姓名（t_expert.user_id → t_user.real_name） */
    private String realName;

    /** 用户头像地址（t_user.avatar_url）；为空时前端显示姓名首字 */
    private String avatarUrl;

    /** 专业领域（t_expert.expert_domain） */
    private String expertDomain;

    /** 专家介绍与服务范围 */
    private String introduction;

    /** 服务工作量统计（问答数、预约数等） */
    private String workloadStat;
}
