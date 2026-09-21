package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 融资申请视图对象，农户侧与银行侧共用。
 *
 * <p>字段按「谁需要看什么」分层：</p>
 * <ul>
 *   <li>农户侧（我的融资）只填申请自身的信息：applyNo、金额、期限、状态、时间；</li>
 *   <li>银行侧（融资管理）与管理员台账额外填农户信息、贷款产品名与审批结论 ——
 *       需求规格说明书 2.9 要求银行用户「查看融资申请与农户信息」后才能给出结论。</li>
 * </ul>
 *
 * <p>不填的字段保持 null，由 {@code spring.jackson.default-property-inclusion=non_null}
 * 在序列化时直接省略，前端无需区分两种形态。</p>
 */
@Data
public class FinanceApplyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long applyId;
    private String applyNo;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String applyStatus;
    private LocalDateTime applyTime;

    /** 申请农户主键 */
    private Long farmerId;

    /** 申请农户姓名（查 t_user.real_name 补齐） */
    private String farmerName;

    /** 申请农户手机号，供银行侧联系核实 */
    private String farmerPhone;

    /** 申请农户经营档案中的主营农产品 */
    private String farmerMainProduct;

    /** 申请的贷款产品主键 */
    private Long loanProductId;

    /** 贷款产品名称（查 t_loan_product.product_name 补齐） */
    private String loanProductName;

    /** 资金用途说明 */
    private String applyPurpose;

    /** 推送至银行侧的时间 */
    private LocalDateTime pushTime;

    /** 审批结论：通过、不通过（取自 t_approval，未审批时为 null） */
    private String approvalResult;

    /** 核准放款金额（审批通过时有值） */
    private BigDecimal loanAmount;

    /** 审批意见 */
    private String approvalOpinion;

    /** 审批时间 */
    private LocalDateTime approvalTime;
}
