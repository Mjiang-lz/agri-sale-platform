package com.rxt.service.impl;

import com.rxt.entity.Knowledge;
import com.rxt.service.HomeService;
import com.rxt.vo.ProductVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 首页模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class HomeServiceImpl implements HomeService {

    @Override
    public List<ProductVO> listHotProducts(int limit) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

    @Override
    public List<Knowledge> listLatestKnowledge(int limit) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

}
