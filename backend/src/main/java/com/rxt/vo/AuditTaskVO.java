package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审核任务视图对象（待审队列与已审记录共用）。
 *
 * <p>审核中心的列表有两个数据来源：</p>
 * <ul>
 *   <li><b>待审核</b>：来自业务表 t_product / t_knowledge / t_comment 中 audit_status = '待审核' 的内容，
 *       此时尚无审核记录，auditId / auditResult / auditRemark / auditorId / auditTime 均为 null；</li>
 *   <li><b>已审核</b>：来自 t_audit 审核流水，auditId 等审核结论字段有值。</li>
 * </ul>
 *
 * <p>用同一个 VO 承载两种形态，前端只需一套渲染逻辑：待审行显示"待审核"标签与提交信息，
 * 已审行额外显示审核结论与意见。</p>
 */
@Data
public class AuditTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审核记录主键（t_audit.audit_id）。
     * 待审核内容尚未产生审核记录，此处为 null。
     */
    private Long auditId;

    /** 审核对象类型：PRODUCT（商品）、KNOWLEDGE（知识）、COMMENT（评论） */
    private String targetType;

    /** 审核对象主键 */
    private Long targetId;

    /**
     * 审核对象标题，供列表直接展示：
     * 商品取 product_name、知识取 title、评论取 content 的摘要（过长截断）。
     */
    private String targetTitle;

    /**
     * 内容全文，仅供详情接口返回（商品取 description、知识取 content、评论取 content）。
     *
     * <p>审核员必须看到全文才能判断是否通过，但列表接口不填此字段——
     * 待审内容可能几百字，列表带全文会让响应体急剧膨胀。</p>
     */
    private String content;

    /** 提交人 ID（商品取 seller_id、知识取 author_id、评论取 user_id） */
    private Long submitterId;

    /** 提交人姓名（查 t_user.real_name 补齐） */
    private String submitterName;

    /** 提交（发布）时间，取业务表的 create_time */
    private LocalDateTime submitTime;

    /**
     * 审核状态：待审核、已通过、未通过。
     * 待审行固定为"待审核"；已审行由 t_audit.audit_result（通过/不通过）映射而来。
     */
    private String auditStatus;

    /** 审核结论：通过、不通过（仅已审行有值） */
    private String auditResult;

    /** 审核意见（仅已审行有值） */
    private String auditRemark;

    /** 审核人 ID（仅已审行有值） */
    private Long auditorId;

    /** 审核人姓名（查 t_user.real_name 补齐） */
    private String auditorName;

    /** 审核时间（仅已审行有值） */
    private LocalDateTime auditTime;
}
