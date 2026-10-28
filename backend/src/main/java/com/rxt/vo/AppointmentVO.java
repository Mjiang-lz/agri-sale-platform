package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 专家预约视图对象，农户侧「我的预约」使用（REQ-11）。
 *
 * <p>{@code t_appointment} 里只存了 {@code expert_id}，姓名与领域分别落在
 * {@code t_expert}（领域）与 {@code t_user}（姓名）两张表上。列表页直接返回
 * 主键的话，前端就得为每一行再查一次专家 —— 那正是「我的预约」这个页面
 * 此前只能把记录存在 localStorage 里的原因之一。这里在后端一次装配好。</p>
 */
@Data
public class AppointmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预约主键 */
    private Long apptId;

    /** 被预约专家主键 */
    private Long expertId;

    /** 预约农户用户主键，专家侧列表使用 */
    private Long farmerId;

    /** 预约农户姓名，专家侧列表使用 */
    private String farmerName;

    /** 专家姓名（查 t_expert.user_id → t_user.real_name） */
    private String expertName;

    /** 专家领域（t_expert.expert_domain） */
    private String expertDomain;

    /** 预约时间 */
    private LocalDateTime apptTime;

    /** 咨询主题 */
    private String apptTopic;

    /** 状态：待处理、已接受、已拒绝、已完成、已取消 */
    private String apptStatus;

    /** 备注 / 专家处理意见 / 取消原因 */
    private String remark;

    /** 提交时间 */
    private LocalDateTime createTime;
}
