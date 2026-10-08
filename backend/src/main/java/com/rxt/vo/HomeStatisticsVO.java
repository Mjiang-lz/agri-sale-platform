package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 首页平台运行数据视图对象。
 *
 * <p><b>为什么要有这个对象，而不是让前端自己去数：</b>官网落地页要展示
 * 「已上架货源 / 有效求购 / 农业知识」这类数量。这些数字背后都带着业务口径 ——
 * 货源要「已通过审核 <b>且</b> 上架」、求购要「有效 <b>且</b> 未过期」、
 * 知识要「已通过审核」。如果每个消费方各自按自己的理解去拉列表再数一遍，
 * 同一句话在落地页、答辩 PPT 和交付文档里就会各算各的，越算越不一致。
 * 口径只在这里定义一次，需要这些数字的地方都调这个接口。</p>
 *
 * <p>字段全部为只读统计量，不含任何用户隐私信息，可对游客开放。</p>
 */
@Data
public class HomeStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 在售货源数：审核通过且处于上架状态，即游客在商品列表里能看到的数量 */
    private Long productCount;

    /** 有效求购数：状态有效且未过有效期，与求购大厅的展示口径一致 */
    private Long demandCount;

    /** 农业知识数：审核通过，即公开可见的知识条目数 */
    private Long knowledgeCount;

    /**
     * 知识分类分布：分类名 → 已通过审核的条数，按条数倒序。
     *
     * <p>键为 {@code category} 的原始取值；分类为空的条目归入「未分类」，
     * 避免出现 null 键（JSON 序列化时会变成字符串 {@code "null"}，前端拿到的是个假分类）。</p>
     */
    private Map<String, Long> knowledgeByCategory;
}
