package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 站内通知实体，对应表 t_notification。
 *
 * <p><b>一行 = 一个事件 × 一个接收人。</b>一单要通知买卖双方就是两行 ——
 * 这是它独立成表、而不是在业务表上加列的原因：塞进 {@code t_order} 会破坏
 * 「订单行一对一」的语义（详见 {@code migrations/2026-10-13_01_notification.sql} 头部说明）。</p>
 *
 * <p>{@code userId} 是<b>接收人</b>、不是发出人。发出方由业务事件隐式决定
 * （订单状态变了，发出方就是系统），所以本表不存 {@code fromUserId} ——
 * 存了也无处展示，只会多一列需要维护的冗余。</p>
 *
 * <p>{@code bizId} 指向哪张表由 {@code type} 决定（多态关联），
 * 与 {@code t_audit.target_id}、{@code t_comment.target_id} 同一处理方式，
 * 因此<b>不建外键</b>。</p>
 */
@Data
@TableName("t_notification")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知主键 */
    @TableId(value = "notification_id", type = IdType.AUTO)
    private Long notificationId;

    /** 接收人（t_user.user_id）。不接受客户端传入，由业务事件指定 */
    private Long userId;

    /** 事件类型，取值见 {@link com.rxt.common.NotificationType} */
    private String type;

    /** 标题，最长 50（与 DDL 列宽一致） */
    private String title;

    /** 正文摘要，最长 200（与 DDL 列宽一致） */
    private String content;

    /** 关联业务主键（订单 / 提问 / 预约 / 申请 id），可为空 */
    private Long bizId;

    /** 是否已读：0 未读，1 已读 */
    private Integer isRead;

    /** 已读时间。NULL = 未读 */
    private LocalDateTime readTime;

    /** 产生时间，由 MybatisMetaObjectHandler 自动填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，由 MybatisMetaObjectHandler 自动填充 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
