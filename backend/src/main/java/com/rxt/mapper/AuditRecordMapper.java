package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.AuditRecord;
import com.rxt.vo.AuditDailyStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核记录数据访问接口（t_audit）。
 */
@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecord> {

    /**
     * 某审核员的累计已审条数（不限时间）。
     *
     * <p>必须传 {@code targetTypes}：t_audit 同时记录 USER / EXPERT 主体审核（属 M1），
     * 不过滤类型会把主体审核也计进来。</p>
     *
     * <p>SQL 见 resources/mapper/AuditRecordMapper.xml。</p>
     *
     * @param auditorId   审核人 ID，取自登录态，不接受客户端传入
     * @param targetTypes 统计范围：PRODUCT / KNOWLEDGE / COMMENT 的一个或多个
     */
    long countByAuditor(@Param("auditorId") Long auditorId,
                        @Param("targetTypes") List<String> targetTypes);

    /**
     * 近 7 日按天统计审核通过数与驳回数（一天一行，只返回<b>有记录</b>的日期）。
     *
     * <p>调用方负责把无记录的日期补 0，凑齐连续的 7 天，见
     * {@code AuditServiceImpl#dailyTrend}。</p>
     *
     * @param startTime    窗口起点（含），取「今天 - 6 天」的 00:00:00
     * @param endTime      窗口终点（不含），取「明天」的 00:00:00 —— 用半开区间而不是
     *                     {@code <= 今天 23:59:59}，避免精确到秒时漏掉当天最后一秒的数据
     * @param targetTypes  统计范围，同 {@link #countByAuditor}
     * @param passResult   审核结论词表中「通过」的取值（Java 侧常量唯一来源）
     * @param rejectResult 审核结论词表中「不通过」的取值
     */
    List<AuditDailyStatVO> selectDailyStats(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("targetTypes") List<String> targetTypes,
                                            @Param("passResult") String passResult,
                                            @Param("rejectResult") String rejectResult);
}
