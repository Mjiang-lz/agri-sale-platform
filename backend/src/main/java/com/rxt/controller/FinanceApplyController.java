package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.Result;
import com.rxt.dto.FinanceApplyDTO;
import com.rxt.entity.FinanceApply;
import com.rxt.security.UserContext;
import com.rxt.service.FinanceApplyService;
import com.rxt.service.impl.FinanceApplyAssembler;
import com.rxt.vo.FinanceApplyVO;
import com.rxt.vo.GuarantorCandidateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 融资服务接口。基础路径 {@code /finance}。
 *
 * <p><b>路径注意</b>：{@code server.servlet.context-path} 已经是 {@code /api}，
 * 这里的 {@code @RequestMapping} 只写 {@code /finance}。若再写成 {@code /api/finance}，
 * 真实路径会变成 {@code /api/api/finance/**}，前端按 {@code /api/finance/**} 调用时
 * 全部 404，再被全局异常处理器的兜底分支伪装成 {@code 500 系统繁忙} ——
 * 排查时极易误判成「后端服务没起来」。</p>
 *
 * <p><b>本类是全项目唯一的融资申请实现。</b>骨架期曾另有一个空的
 * {@code FinanceController}（同样 {@code /finance}，方法体只有 {@code // TODO 待实现}，
 * 返回值恒为 {@code null} / {@code emptyList()}）。它会让 {@code POST /finance/apply}
 * 产生 Ambiguous mapping 导致应用起不来，也会让前端拿到「提交成功但库里没数据」的假成功。
 * 该空壳已连同 {@code FinanceService} / {@code FinanceServiceImpl} 一并删除，
 * <b>请勿再新建同路径的融资 Controller</b>。</p>
 */
@RestController
@RequestMapping("/finance")
public class FinanceApplyController {

    @Autowired
    private FinanceApplyService financeApplyService;

    @Autowired
    private FinanceApplyAssembler financeApplyAssembler;

    /**
     * 提交融资意向。
     */
    @PostMapping("/apply")
    public Result<Void> apply(@RequestBody FinanceApplyDTO dto) {
        // ⚠️ 团队红线：用户ID必须从JWT取，严禁前端传！
        Long userId = UserContext.currentUserId();
        financeApplyService.submitApply(userId, dto);
        return Result.success();
    }

    /**
     * 我的融资进度查询（农户侧，只返回本人的申请）。
     *
     * <p>分页参数带默认值，兼容前端不带参直接调用的写法。
     * 装配时 {@code withApproval=false} —— 农户侧不需要审批结论与经营档案，
     * 可少两次关联查询。</p>
     */
    @GetMapping("/apply/my")
    public Result<IPage<FinanceApplyVO>> myApplies(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        Long userId = UserContext.currentUserId();
        Page<FinanceApply> page = financeApplyService.getMyApply(userId, (int) pageNum, (int) pageSize);

        Page<FinanceApplyVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(financeApplyAssembler.assemble(page.getRecords(), false));
        return Result.success(voPage);
    }

    /**
     * 联合融资人智能推荐（REQ-07）。
     *
     * <p>按经营特征（品类 / 地区 / 额度 / 期限 / 经营年限）算出的绝对匹配度倒序返回，
     * 并<b>过滤掉已经在进行中小组里的农户</b> —— 推一个已经组好队的人，
     * 用户点下去必然失败。</p>
     *
     * <p>申请人尚未填写农户经营档案时返回 500 + 明确提示，而不是空列表：
     * 「平台里没有别的农户」与「你自己缺档案」是两回事，不该用同一个响应表达。</p>
     */
    @GetMapping("/recommend")
    public Result<List<GuarantorCandidateVO>> recommend() {
        Long userId = UserContext.currentUserId();
        return Result.success(financeApplyService.recommendGuarantors(userId));
    }

    /**
     * 银行侧审批。
     *
     * @deprecated 融资审批的单一实现已收敛到 {@code LoanAdminService.approve}
     *         （见 {@code LoanAdminController} 与 {@code AdminFinanceController}）。
     *         本入口保留只为兼容既有调用，新代码请勿再使用。
     */
    @Deprecated
    @PostMapping("/bank/approve")
    public Result<Void> bankApprove(@RequestParam Long applyId,
                                    @RequestParam String approvalResult,
                                    @RequestParam(required = false) BigDecimal loanAmount,
                                    @RequestParam(required = false) String approvalOpinion) {
        financeApplyService.bankApprove(applyId, approvalResult, loanAmount, approvalOpinion);
        return Result.success();
    }
}
