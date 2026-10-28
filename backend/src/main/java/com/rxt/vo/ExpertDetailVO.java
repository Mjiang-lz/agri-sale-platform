package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 专家详细介绍（概设 UI-EXP-03「专家介绍」的详情页形态，需求 9）。
 *
 * <p><b>为什么要详情页而不能只靠一张卡片</b>：用户挑专家是照着自己的**难题**挑的
 * （「地里的柑橘该打什么药」「果子卖不掉能存成干货吗」），而一行 introduction
 * 只够写「省农科院研究员」这种履历 —— 从用户角度看，他看完仍然不知道这位专家
 * 能不能解决自己的问题，于是要么随便约第一位、要么干脆不约。
 * 详情页把「擅长领域 / 实际答复过的问题 / 受理过的预约主题」摊开，
 * 用户才是拿自己的问题去对号入座。</p>
 *
 * <p><b>数据全部实时聚合，不另存冗余字段</b>：答复数、预约数、热门主题
 * 都从 {@code t_question} / {@code t_appointment} 现算。
 * 库里已有 {@code workload_stat} 是「已回复提问 42 条」这样的**手写字符串**，
 * 与真实数据对不上（赵国强实际答复数远不到 42）；详情页再展示一遍这种数字，
 * 用户点进去一看对不上，整页的可信度就没了。</p>
 */
@Data
public class ExpertDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 专家主键 */
    private Long expertId;

    /** 关联的用户主键 */
    private Long userId;

    /** 专家姓名 */
    private String realName;

    /** 用户头像地址；为空时前端显示姓名首字 */
    private String avatarUrl;

    /** 专业领域 */
    private String expertDomain;

    /** 专家介绍与服务范围 */
    private String introduction;

    /** 服务工作量统计（库里的手写文案，保留兼容既有页面） */
    private String workloadStat;

    /** 该专家答复过的提问数（实时统计，status = 已回复） */
    private Integer answeredCount;

    /** 该专家被指定过的提问总数（含待回复），用于「有多少人在问我」 */
    private Integer askedCount;

    /** 该专家收到的预约总数 */
    private Integer appointmentCount;

    /** 该专家已完成 / 已接受的预约数（服务真正闭环的次数） */
    private Integer finishedCount;

    /** 该专家答复过的提问摘要（最多 5 条，取最近），让用户看到「同问题的人问过什么」 */
    private List<AnsweredQuestion> recentAnswers;

    /** 该专家的预约主题（最多 5 个高频词），直观呈现找他咨询什么 */
    private List<String> appointmentTopics;

    /** 一条已答复的提问摘要 */
    @Data
    public static class AnsweredQuestion implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 提问内容摘要（已在服务端截断，前端直接展示） */
        private String content;
        /** 提问时间（yyyy-MM-dd） */
        private String askTime;
    }
}
