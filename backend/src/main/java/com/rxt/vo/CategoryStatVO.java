package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品品类统计（概设 UI-EXP-01 商品货源列表的分类筛选、首页品类入口）。
 *
 * <p><b>为什么要后端出这个接口</b>：品类此前是前端硬编码的六个字符串
 * （蔬菜/水果/粮油/干货/水产/畜禽产品），散落在 4 个视图里。这带来一个真实缺陷 ——
 * 任何人发布一个「农资」或「茶叶」的商品，它在前台下拉里选不到、
 * 在首页六个圆入口里也点不到，只能靠手输关键词找到，成了孤儿品类。
 * 「分类要覆盖所有商品」这条要求，靠前端维护一份名单是做不到的，
 * 只有从库里 distinct 出来才算数。</p>
 *
 * <p><b>口径</b>：只统计「审核通过且已上架」的商品。这与 {@code GET /trade/products}
 * 的可见口径一致 —— 否则筛选下拉里会出现一个点进去是空结果的品类
 * （有商品但全是待审核/已下架），那比不显示这个选项更糟。</p>
 */
@Data
public class CategoryStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 品类名（t_product.category，精确匹配筛选值） */
    private String category;

    /** 该品类下在架商品数，用于首页圆入口排序与「N 件在售」文案 */
    private Integer productCount;
}
