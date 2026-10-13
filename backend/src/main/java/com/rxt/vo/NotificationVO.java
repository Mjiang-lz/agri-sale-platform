package com.rxt.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 站内通知视图对象。
 *
 * <p>比实体多一个 {@code link}：由 {@link com.rxt.common.NotificationType} 按 {@code type}
 * 映射出的前端跳转目标。让服务端算而不是前端拼的原因见
 * {@code docs/接口约定-站内通知.md} §3.3 —— 「订单通知跳哪一页」这个知识只应存在一处。</p>
 */
@Data
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long notificationId;

    /** 事件类型：ORDER / QA / APPOINTMENT / FINANCE */
    private String type;

    private String title;

    private String content;

    /** 关联业务主键，可能为空 */
    private Long bizId;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    /** 已读时间，未读时为 null */
    private LocalDateTime readTime;

    private LocalDateTime createTime;

    /** 前端跳转目标；类型认不出时为 null，前端不做跳转即可 */
    private String link;
}
