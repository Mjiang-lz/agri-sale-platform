package com.rxt.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员编辑专家档案的入参。
 *
 * <p>在 {@link ExpertProfileUpdateDTO} 之上<b>只多一个 {@code auditStatus}</b>。</p>
 *
 * <p><b>为什么另立一个类，而不是把 {@code auditStatus} 加进专家侧那个 DTO</b>：
 * 专家侧接口的签名收的是 {@code ExpertProfileUpdateDTO}，只要那个类里没有
 * {@code auditStatus}，专家就不可能通过自己的接口改审核状态 —— 这是<b>编译期</b>的保证，
 * 不依赖"记得在 Service 里过滤掉某个字段"这种运行时约定。一旦有人日后把
 * {@code auditStatus} 顺手加进基类，专家就能自己批自己，而那是很难在 review 里看出来的。</p>
 *
 * <p>另：本接口改 {@code auditStatus} 属<b>资料维护</b>，不写 {@code t_audit} 审核留痕，
 * 因此不计入 {@code GET /api/audit/statistics} 的审核工作量。这不是疏忽 ——
 * {@code POST /api/audit/content/EXPERT/{id}} 当前并不存在（内容审核模块只管辖
 * PRODUCT / KNOWLEDGE / COMMENT，验类型时会 400 拒掉 EXPERT），所以本接口是今天
 * 唯一能改专家审核状态的入口。详见 {@code ExpertService#updateProfileForAdmin}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminExpertUpdateDTO extends ExpertProfileUpdateDTO {

    /**
     * 审核状态。
     *
     * <p>用白名单正则而不是在这里内联一个枚举：三个取值（待审核 / 已通过 / 未通过）
     * 与 {@code t_expert.audit_status} 的 DDL 注释逐字对应，写在注解里最直观，
     * 也避免为一个三值词表再建枚举类导致全仓出现第二处定义。</p>
     */
    @Pattern(regexp = "待审核|已通过|未通过", message = "审核状态只能是「待审核」「已通过」「未通过」三者之一")
    private String auditStatus;
}
