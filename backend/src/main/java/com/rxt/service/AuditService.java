package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.AuditActionDTO;
import com.rxt.vo.AuditStatisticsVO;
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

    /**
     * 提交审核结论：通过 / 驳回（一个入口覆盖三类内容，内部按类型分流到对应业务表）。
     *
     * <p>在自己的事务内完成两件事，要么全成、要么全不成：</p>
     * <ol>
     *   <li>更新被审内容的 {@code audit_status}；
     *       商品额外联动上下架状态（通过即上架、驳回即下架），
     *       否则审核通过的商品仍处于下架状态，加购与下单会被一并拦下；</li>
     *   <li>向 t_audit 写入一条审核流水（结论、意见、审核人、审核时间）。</li>
     * </ol>
     *
     * <p>只允许审核当前为「待审核」的内容。已审过的内容重复审核会被拒绝，
     * 这样两个审核员同时点开同一条时，后提交的那次不会静默覆盖前一次的结论。</p>
     *
     * @param targetType 内容类型：PRODUCT / KNOWLEDGE / COMMENT
     * @param targetId   内容主键
     * @param auditorId  审核人 ID，取自登录态（UserContext.currentUserId()），不接受客户端传入
     * @param action     审核结论与审核意见（驳回时意见必填）
     * @return 审核后的内容视图（含本次审核流水），前端可直接用它刷新当前行
     */
    AuditTaskVO auditContent(String targetType, Long targetId, Long auditorId, AuditActionDTO action);

    /**
     * 审核工作量统计：当前审核员的累计已审数 + 近 7 日审核趋势。
     *
     * <p>数据源是审核流水 t_audit（统计「审核动作发生了多少」），
     * 与看板统计「内容当前是什么状态」互补，两者不重复 —— 见
     * {@code docs/接口约定-M6内容审核.md} 第八节。</p>
     *
     * <p>严格限定内容类型（PRODUCT / KNOWLEDGE / COMMENT）：t_audit 同时记录
     * USER / EXPERT 主体审核（属 M1），不限定类型会把它们算进来。</p>
     *
     * @param auditorId  「我的已审数」的口径来源，取自登录态（UserContext.currentUserId()），
     *                   不接受客户端传入 —— 否则任何登录用户都能查别人的工作量
     * @param targetType 可选：PRODUCT / KNOWLEDGE / COMMENT；为空表示三类内容合计
     * @return 统计视图；{@code last7Days} 固定 7 个元素、含今天、无数据的日期补 0
     */
    AuditStatisticsVO statistics(Long auditorId, String targetType);

}
