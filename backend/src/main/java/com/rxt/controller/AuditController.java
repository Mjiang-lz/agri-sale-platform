package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.security.UserContext;
import com.rxt.service.AuditService;
import com.rxt.vo.AuditTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * 审核记录详情（已完成审核的流水，含结论与意见）。
     */
    @GetMapping("/{auditId}")
    public Result<AuditTaskVO> auditDetail(@PathVariable Long auditId) {
        return Result.success(auditService.auditDetail(auditId));
    }
}
