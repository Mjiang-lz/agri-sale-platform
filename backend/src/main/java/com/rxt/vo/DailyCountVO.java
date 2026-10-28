package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板「按日新增」趋势点。
 */
@Data
public class DailyCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期，yyyy-MM-dd */
    private String date;

    /** 当日新增数量；该日无数据时为 0，不返回 null，前端无需补空 */
    private long count;

    public DailyCountVO() {
    }

    public DailyCountVO(String date, long count) {
        this.date = date;
        this.count = count;
    }
}
