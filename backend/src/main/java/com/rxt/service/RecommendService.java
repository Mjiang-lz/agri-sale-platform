package com.rxt.service;

import java.util.List;

/**
 * 智能推荐模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface RecommendService {

    /**
     * 基于经营特征推荐联合融资人。
     */
    List<Long> recommendPartners(Long farmerId, int limit);

}
