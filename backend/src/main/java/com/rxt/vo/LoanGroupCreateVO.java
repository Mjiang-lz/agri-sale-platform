package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 组建联合贷款小组的结果视图对象。
 *
 * <p><b>为什么建组要返回结果体，而不是像原来那样返回 {@code Result<Void>}：</b>
 * 建组是「一个发起人 + N 个被推荐人」的一次性动作，其中<b>部分人可能进不来</b> ——
 * 例如该农户已经在别的进行中小组里，或者他还没提交同产品的融资申请。
 * 如果接口只返回「成功」，这些被跳过的人就<b>凭空消失</b>了：发起人以为
 * 小组已经凑齐，实际上只有自己一个人。这与「空实现直接返回成功」是同一类
 * 故障 —— 前端提示成功、库里没数据，只是这次没数据的是<b>成员</b>。
 *
 * <p>因此把「谁进来了、谁没进来、为什么」明确回传，让调用方能如实展示。</p>
 */
@Data
public class LoanGroupCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 小组主键 */
    private Long groupId;

    /** 小组名称 */
    private String groupName;

    /** 组长用户主键 */
    private Long leaderId;

    /** 当前成员数（含组长） */
    private Integer memberCount;

    /**
     * 该产品起贷所需的联合人数下限（t_loan_product.min_group_size）。
     *
     * <p>回传它是为了让前端自己判断「人够了没有」并给出对应提示，
     * 而不是由后端在这里悄悄拦掉 —— 人数不足时小组停留在「组建中」等待
     * 后续加入是合法状态，不是错误。</p>
     */
    private Integer minGroupSize;

    /** 目标融资金额合计（各成员申请额度之和） */
    private BigDecimal targetAmount;

    /** 小组状态 */
    private String groupStatus;

    /** 本次实际入组的成员（含组长，组长恒为第一条） */
    private List<Member> joined = new ArrayList<>();

    /** 本次未能入组的被推荐人及原因，为空表示全部入组成功 */
    private List<Skipped> skipped = new ArrayList<>();

    /** 已入组的成员摘要 */
    @Data
    public static class Member implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 成员用户主键 */
        private Long userId;

        /** 成员姓名 */
        private String realName;

        /** 该成员并入本组的申请额度 */
        private BigDecimal applyAmount;

        /** 成员角色：组长 / 成员 */
        private String memberRole;
    }

    /** 未能入组的候选人 */
    @Data
    public static class Skipped implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 候选人用户主键 */
        private Long userId;

        /** 候选人姓名 */
        private String realName;

        /** 未能入组的原因，直接面向用户，可原文展示 */
        private String reason;
    }
}
