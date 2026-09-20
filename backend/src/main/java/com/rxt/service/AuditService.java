package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.vo.AuditTaskVO;

import java.util.List;

/**
 * 内容审核模块业务接口（M6 / REQ-15）。
 *
 * <p>审核中心的数据来自两处，实现时需按状态路由：</p>
 * <ul>
 *   <li><b>待审核队列</b>：t_product / t_knowledge / t_comment 中 audit_status = '待审核' 的内容。
 *       表中不落任务记录，待审内容本身即是任务；</li>
 *   <li><b>已审核记录</b>：t_audit 审核流水（audit_result = 通过 / 不通过）。</li>
 * </ul>
 *
 * <p>实现类位于 com.rxt.service.impl。</p>
 */
public interface AuditService {

    /**
     * 审核任务列表（统一入口，按状态路由数据源）。
     *
     * @param targetType  内容类型：PRODUCT / KNOWLEDGE / COMMENT；为空表示不限类型
     * @param auditStatus 审核状态：待审核 / 已通过 / 未通过；为空时默认按"待审核"处理
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页条数
     */
    IPage<AuditTaskVO> pageAudits(String targetType, String auditStatus, long pageNum, long pageSize);

    /**
     * 待审核队列（内部调用审计列表时固定状态为"待审核"的快捷方式）。
     *
     * <p>待审任务不预分派审核员，任一审核员均可查看与领取，因此不按 auditorId 过滤。</p>
     *
     * @param targetType 内容类型：PRODUCT / KNOWLEDGE / COMMENT；为空表示不限类型
     */
    IPage<AuditTaskVO> pagePendingAudits(String targetType, long pageNum, long pageSize);

    /**
     * 我已审核的记录列表，按审核时间倒序。
     *
     * @param auditorId  审核员 ID，取自登录态（UserContext.currentUserId()），不接受客户端传入
     * @param targetType 内容类型；为空表示不限类型
     */
    IPage<AuditTaskVO> pageMyAudits(Long auditorId, String targetType, long pageNum, long pageSize);

    /**
     * 审核记录详情：t_audit 记录 + 被审内容的标题、提交人等展示信息。
     *
     * @param auditId 审核记录主键；记录不存在时抛出业务异常
     */
    AuditTaskVO auditDetail(Long auditId);

    /**
     * 待审内容详情：返回内容全文（商品描述、知识正文、评论原文）供审核员判断。
     *
     * <p>与 {@link #auditDetail(Long)} 的区别：待审内容在 t_audit 中尚无记录、没有 auditId，
     * 只能按 targetType + targetId 定位；若该内容已有历史审核记录，则附带最近一次的结论。</p>
     *
     * @param targetType 内容类型：PRODUCT / KNOWLEDGE / COMMENT
     * @param targetId   内容主键
     */
    AuditTaskVO contentDetail(String targetType, Long targetId);

    /**
     * 某条内容的审核历史：同一 targetType + targetId 的全部审核记录，按审核时间倒序。
     *
     * <p>用于"驳回后修改再提交"的多次审核回溯，例如第 1 次不通过、第 2 次通过。</p>
     *
     * @param targetType 内容类型：PRODUCT / KNOWLEDGE / COMMENT
     * @param targetId   内容主键
     */
    List<AuditTaskVO> listAuditLogs(String targetType, Long targetId);

}
