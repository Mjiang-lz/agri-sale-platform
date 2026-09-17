package com.rxt.service.impl;

import com.rxt.service.RecommendService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 智能推荐模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class RecommendServiceImpl implements RecommendService {

    @Override
    public List<Long> recommendPartners(Long farmerId, int limit) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

}
