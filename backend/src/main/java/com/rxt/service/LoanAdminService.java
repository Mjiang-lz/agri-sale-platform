package com.rxt.service;

import com.rxt.vo.FinanceApplyVO;
import java.math.BigDecimal;
import java.util.List;

/**
 * 融资管理（银行侧）模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》2.9 融资管理。本模块
 * 仅对「银行用户」开放；平台管理员的只读台账走 /api/admin/finance，不共用本接口。</p>
 */
public interface LoanAdminService {

    /**
     * 查询已推送至银行侧的融资申请。
     *
     * @param status 按申请状态筛选；为空表示返回全部已推送的申请（含已出结论的）
     */
    List<FinanceApplyVO> listPushedApplies(String status);

    /**
     * 审批融资申请：写入 t_approval 审批流水，并把申请状态回写为
     * 「审批通过」或「审批未通过」。
     *
     * @param applyId    融资申请主键
     * @param bankUserId 审批人（银行用户），由 Controller 从登录态取得
     * @param pass       true 通过，false 不通过
     * @param loanAmount 核准放款金额，通过时必填
     * @param opinion    审批意见，不通过时必填
     */
    void approve(Long applyId, Long bankUserId, boolean pass, BigDecimal loanAmount, String opinion);

}
