package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.AuditActionDTO;
import com.rxt.security.UserContext;
import com.rxt.service.AuditService;
import com.rxt.vo.AuditStatisticsVO;
import com.rxt.vo.AuditTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内容审核接口。基础路径 /api/audit。
 *
 * <p>审核属平台治理职能，全部接口都要求登录，且角色校验（仅管理员）在
 * Service 层由 {@code PermissionChecker} 完成。</p>
 *
 * <p>审核人身份一律取自登录态 {@link UserContext}，不接受客户端传入 ——
 * 用请求参数传身份可以被随意伪造，任何登录用户都能以他人名义审核。</p>
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 审核任务列表（统一入口）。
     * 不传 auditStatus 时默认查"待审核"（审核后台首屏即待办队列）。
     */
    @GetMapping("/list")
    public Result<IPage<AuditTaskVO>> pageAudits(@RequestParam(required = false) String targetType,
                                                 @RequestParam(required = false) String auditStatus,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(auditService.pageAudits(targetType, auditStatus, pageNum, pageSize));
    }

    /**
     * 待审核队列。
     * 待审任务不预分派审核员，任一审核员都能看到全部待审内容，因此不传 auditorId。
     */
    @GetMapping("/pending")
    public Result<IPage<AuditTaskVO>> pagePendingAudits(@RequestParam(required = false) String targetType,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(auditService.pagePendingAudits(targetType, pageNum, pageSize));
    }

    /**
     * 我已审核的记录。审核人身份取自登录态。
     */
    @GetMapping("/my")
    public Result<IPage<AuditTaskVO>> pageMyAudits(@RequestParam(required = false) String targetType,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(auditService.pageMyAudits(
                UserContext.currentUserId(), targetType, pageNum, pageSize));
    }

    /**
     * 某条内容的审核历史（按审核时间倒序）。
     * 放在 {auditId} 之前声明更清晰，Spring 也会优先匹配字面量路径。
     */
    @GetMapping("/logs")
    public Result<List<AuditTaskVO>> listAuditLogs(@RequestParam String targetType,
                                                   @RequestParam Long targetId) {
        return Result.success(auditService.listAuditLogs(targetType, targetId));
    }

    /**
     * 待审（或已审）内容详情：返回内容全文，供审核员判断。
     * 例：/audit/content/PRODUCT/24
     */
    @GetMapping("/content/{targetType}/{targetId}")
    public Result<AuditTaskVO> contentDetail(@PathVariable String targetType,
                                             @PathVariable Long targetId) {
        return Result.success(auditService.contentDetail(targetType, targetId));
    }

    /**
     * 提交审核结论（通过 / 不通过）。
     *
     * <p>与上面的内容详情同一路径，靠 HTTP 方法区分语义：GET 看内容全文，POST 提交结论。
     * 一个入口覆盖三类内容，类型差异（商品要联动上下架、知识/评论不用）由 Service 内部消化。</p>
     *
     * <p>审核人取自登录态，请求体只带结论与意见。</p>
     */
    @PostMapping("/content/{targetType}/{targetId}")
    public Result<AuditTaskVO> auditContent(@PathVariable String targetType,
                                            @PathVariable Long targetId,
                                            @RequestBody @Valid AuditActionDTO action) {
        return Result.success(auditService.auditContent(
                targetType, targetId, UserContext.currentUserId(), action));
    }

    /**
     * 审核工作量统计：我的累计已审数 + 近 7 日审核趋势。
     *
     * <p>与看板 {@code /admin/dashboard/statistics} 分工：看板给「内容当前状态」
     * （待审总数、状态分布），本接口给「审核动作量」。两者不重复，见
     * docs/接口约定-M6内容审核.md 第八节。</p>
     *
     * <p>路径是字面量，声明在 {@code /{auditId}} 之前更清晰（Spring 也会优先匹配字面量路径）。</p>
     *
     * @param targetType 可选：PRODUCT / KNOWLEDGE / COMMENT；为空表示三类内容合计
     */
    @GetMapping("/statistics")
    public Result<AuditStatisticsVO> statistics(@RequestParam(required = false) String targetType) {
        // 「我的已审数」的口径来源是登录态，不接受客户端传 auditorId
        return Result.success(auditService.statistics(UserContext.currentUserId(), targetType));
    }

    /**
     * 审核记录详情（已完成审核的流水，含结论与意见）。
     */
    @GetMapping("/{auditId}")
    public Result<AuditTaskVO> auditDetail(@PathVariable Long auditId) {
        return Result.success(auditService.auditDetail(auditId));
    }
}
