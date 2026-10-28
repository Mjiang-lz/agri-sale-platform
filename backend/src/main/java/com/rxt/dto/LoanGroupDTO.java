package com.rxt.dto;

import lombok.Data;

import java.util.List;

/**
 * 组建联合贷款小组入参。
 *
 * <p><b>改动说明（相对骨架版）：</b></p>
 * <ul>
 *   <li><b>删掉 {@code description}</b>：{@code t_loan_group} 表里<b>没有</b>这个列，
 *       骨架期前端把「拟邀请联保人：张三、李四」这段人名塞进它，接口收下之后
 *       无处可写 —— 这正是建组表现为「提示成功、库里没数据」的原因之一。
 *       成员信息现在有了正经字段 {@link #memberIds}，不需要再用备注传递。</li>
 *   <li><b>新增 {@link #memberIds}</b>：被推荐的联保候选人。</li>
 *   <li><b>新增 {@link #loanProductId}</b>：用于定位发起人本次对应的融资申请。
 *       不传时回退到「最近一条待匹配申请」，传了则精确匹配该产品的申请，
 *       避免一个农户同时有多笔待匹配申请时组错产品。</li>
 * </ul>
 */
@Data
public class LoanGroupDTO {

    /** 小组名称，不传则按「产品名 + 联合小组」自动生成 */
    private String groupName;

    /** 本次拟邀请的联保候选人用户主键，可为空（只建组，后续由成员自行加入） */
    private List<Long> memberIds;

    /** 本次组队对应的贷款产品主键，可为空 */
    private Long loanProductId;
}
