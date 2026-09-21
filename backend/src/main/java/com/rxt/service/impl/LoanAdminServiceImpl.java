package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.entity.Approval;
import com.rxt.entity.FinanceApply;
import com.rxt.mapper.ApprovalMapper;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.LoanAdminService;
import com.rxt.vo.FinanceApplyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 融资管理（银行侧）模块业务实现。
 *
 * <p>本模块整体只对「银行用户」开放（需求规格说明书 2.9 融资管理：智能匹配农户、贷款审批；
 * 平台管理员不参与业务闭环 —— 管理后台看的是只读台账，见 {@link AdminFinanceServiceImpl}），
 * 因此每个方法进入业务逻辑前先做角色校验，非银行用户一律 403。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanAdminServiceImpl implements LoanAdminService {

    /** 申请状态：已推送银行（银行侧待办） */
    private static final String STATUS_PUSHED = "已推送银行";

    /** 申请状态：审批通过 */
    private static final String STATUS_APPROVED = "审批通过";

    /** 申请状态：审批未通过 */
    private static final String STATUS_REJECTED = "审批未通过";

    /** 审批结果：通过 */
    private static final String RESULT_PASS = "通过";

    /** 审批结果：不通过 */
    private static final String RESULT_REJECT = "不通过";

    private final PermissionChecker permissionChecker;
    private final FinanceApplyMapper financeApplyMapper;
    private final ApprovalMapper approvalMapper;
    private final FinanceApplyAssembler financeApplyAssembler;

    @Override
    public List<FinanceApplyVO> listPushedApplies(String status) {
        permissionChecker.requireRole(RoleConstant.BANK);

        LambdaQueryWrapper<FinanceApply> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(FinanceApply::getApplyStatus, status.trim());
        } else {
            // 「已推送至银行侧」的判定依据是 push_time 有值，而不是枚举某一组状态 ——
            // 流转中的其它环节（待匹配、已组建小组）还没推给银行，银行侧本就不该看到。
            wrapper.isNotNull(FinanceApply::getPushTime);
        }
        wrapper.orderByDesc(FinanceApply::getPushTime).orderByDesc(FinanceApply::getApplyId);

        return financeApplyAssembler.assemble(financeApplyMapper.selectList(wrapper), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applyId, Long bankUserId, boolean pass,
                        BigDecimal loanAmount, String opinion) {
        permissionChecker.requireRole(RoleConstant.BANK);

        FinanceApply apply = financeApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "融资申请不存在");
        }
        if (!STATUS_PUSHED.equals(apply.getApplyStatus())) {
            // 重复审批会让同一笔申请出现两条结论、申请人看到的状态来回跳，
            // 因此只允许对仍处于「已推送银行」的申请作出结论。
            throw new BizException("该申请当前状态为「" + apply.getApplyStatus() + "」，不可审批");
        }

        if (pass) {
            if (loanAmount == null) {
                throw new BizException("审批通过时必须填写放款金额");
            }
            if (apply.getApplyAmount() != null && loanAmount.compareTo(apply.getApplyAmount()) > 0) {
                throw new BizException("核准放款金额不得超过申请额度 " + apply.getApplyAmount());
            }
        } else if (!StringUtils.hasText(opinion)) {
            throw new BizException("审批不通过时必须填写审批意见");
        }

        String result = pass ? RESULT_PASS : RESULT_REJECT;

        Approval approval = new Approval();
        approval.setApplyId(applyId);
        approval.setBankUserId(bankUserId);
        approval.setApprovalResult(result);
        approval.setLoanAmount(pass ? loanAmount : null);
        approval.setApprovalOpinion(StringUtils.hasText(opinion) ? opinion.trim() : null);
        approval.setApprovalTime(LocalDateTime.now());
        approvalMapper.insert(approval);

        // 回写申请状态，农户侧「我的融资」随之更新（结果回传，对应概要设计 5.2 内部接口）
        FinanceApply update = new FinanceApply();
        update.setApplyId(applyId);
        update.setApplyStatus(pass ? STATUS_APPROVED : STATUS_REJECTED);
        financeApplyMapper.updateById(update);

        log.info("融资审批完成 applyId={} bankUserId={} result={}", applyId, bankUserId, result);
    }
}
