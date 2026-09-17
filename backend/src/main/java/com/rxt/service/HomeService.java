package com.rxt.service;

import com.rxt.entity.Knowledge;
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
     */
    List<ProductVO> listHotProducts(int limit);

    /**
     * 首页最新农业知识。
     */
    List<Knowledge> listLatestKnowledge(int limit);

}
