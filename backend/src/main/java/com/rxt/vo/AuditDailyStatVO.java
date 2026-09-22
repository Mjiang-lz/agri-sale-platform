package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 审核统计的「按日趋势点」：某一天的审核通过数 / 驳回数。
 *
 * <p>为什么不复用看板的 {@link DailyCountVO}：那个只能承载<b>单个</b>计数。
 * 而前端（管理后台审核页）要的是每天同时给出 reviewed 与 rejected，
 * 若用两个 DailyCountVO 列表，就变成两个长度必须严格对齐的平行数组，
 * 前端还得自己按日期合并 —— 反而更容易出错。</p>
 *
 * <p>字段形状由前端确定，见 {@code docs/接口约定-M6内容审核.md} 第 8 节。</p>
 */
@Data
public class AuditDailyStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期，yyyy-MM-dd */
    private String date;

    /** 当日审核通过条数；该日无数据时为 0，不返回 null */
    private long reviewed;

    /** 当日审核驳回条数；该日无数据时为 0，不返回 null */
    private long rejected;

    public AuditDailyStatVO() {
    }

    public AuditDailyStatVO(String date, long reviewed, long rejected) {
        this.date = date;
        this.reviewed = reviewed;
        this.rejected = rejected;
    }
}
