package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 联合融资人（联保候选人）视图对象，对应 REQ-07「联合融资人智能推荐」。
 *
 * <p><b>为什么不复用 {@link UserVO}：</b>{@code UserVO} 是「个人中心视图对象」，
 * 承载的是账号自身的资料（手机号、角色、菜单权限）。推荐候选人需要的是
 * <b>经营特征</b>与<b>匹配度</b>这两类只有融资场景才存在的信息 ——
 * 前者塞进 UserVO 等于让每次「个人中心」请求都多查一次经营档案，
 * 后者更是无字段可放（这正是当初这个方法只能 {@code return new ArrayList<>()} 的原因）。
 * 单独建 VO 后，既不用改动个人中心的返回契约，推荐结果也有地方表达「为什么推荐他」。</p>
 *
 * <p><b>刻意不返回 {@code phone}：</b>详情页的农户信息里 {@code ProductDetailVO}
 * 也没带手机号。联保候选人之间是「平台撮合」关系，是否互相暴露联系方式应由
 * 双方同意后由平台转达，不应在推荐列表里直接给出 —— 那等于把全平台农户的
 * 手机号开放给任何一个农户账号，批量翻取即可爬走。</p>
 */
@Data
public class GuarantorCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 候选农户用户主键 */
    private Long userId;

    /** 候选农户姓名（t_user.real_name） */
    private String realName;

    /** 头像地址 */
    private String avatarUrl;

    /** 主营农产品（t_farmer_profile.main_product） */
    private String mainProduct;

    /** 经营规模描述（t_farmer_profile.scale_desc） */
    private String scaleDesc;

    /** 从 credit_record 解析出的经营地区，解析不到为 null */
    private String region;

    /**
     * 匹配度，取值 0~100 的整数（百分数）。
     *
     * <p>是<b>绝对分</b>而不是「在候选集合里的相对排名」：如果按集合内归一化，
     * 一个只有 2 个农户的小库里也会出现「100% 匹配」，而实际上
     * 两人可能品类、地区、额度全不一样。绝对分才经得起追问。</p>
     */
    private Integer matchRate;

    /** 匹配理由，逐项说明命中了哪些经营特征，供列表直接展示 */
    private String matchReason;
}
