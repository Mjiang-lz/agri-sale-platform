package com.rxt.service.impl;

import com.rxt.dto.FinanceApplyDTO;
import com.rxt.service.FinanceService;
import com.rxt.vo.FinanceApplyVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 融资服务模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class FinanceServiceImpl implements FinanceService {

    @Override
    public Long submitApply(Long farmerId, FinanceApplyDTO dto) {
        // TODO 待实现：需返回真实主键 / 计数
        return null;
    }

    @Override
    public List<FinanceApplyVO> listMyApplies(Long farmerId) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

}
