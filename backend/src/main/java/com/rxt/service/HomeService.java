package com.rxt.service;

import com.rxt.entity.Knowledge;
import com.rxt.vo.HomeStatisticsVO;
import com.rxt.vo.ProductVO;

import java.util.List;

/**
 * 首页模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface HomeService {

    /**
     * 首页推荐商品。
     *
     * <p>返回「审核通过且已上架」的最新货源，条数不超过 {@code limit}。</p>
     *
     * @param limit 期望条数，超出合理范围会被收敛（见实现类说明）
     */
    List<ProductVO> listHotProducts(int limit);

    /**
     * 首页最新农业知识。
     *
     * <p>只返回审核通过的知识条目，按发布时间倒序取前 {@code limit} 条。</p>
     *
     * @param limit 期望条数，超出合理范围会被收敛（见实现类说明）
     */
    List<Knowledge> listLatestKnowledge(int limit);

    /**
     * 首页平台运行数据：在售货源、有效求购、已通过审核的知识及其分类分布。
     *
     * <p>供官网落地页展示真实平台数据使用。只返回统计量，不含任何用户信息。</p>
     */
    HomeStatisticsVO statistics();

}
