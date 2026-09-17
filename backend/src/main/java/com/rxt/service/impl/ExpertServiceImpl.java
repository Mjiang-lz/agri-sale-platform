package com.rxt.service.impl;

import com.rxt.entity.Knowledge;
import com.rxt.service.ExpertService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 专家助力模块业务实现。
 *
 * <p>骨架阶段仅保留方法签名与依赖注入，具体业务规则待按功能点逐个补齐。</p>
 */
@Service
public class ExpertServiceImpl implements ExpertService {

    @Override
    public List<Knowledge> listKnowledge(String category) {
        // TODO 待实现：返回真实查询结果
        return java.util.Collections.emptyList();
    }

    @Override
    public Long askQuestion(Long askerId, Long expertId, String content) {
        // TODO 待实现：需返回真实主键 / 计数
        return null;
    }

    @Override
    public Long makeAppointment(Long farmerId, Long expertId, LocalDateTime apptTime, String topic) {
        // TODO 待实现：需返回真实主键 / 计数
        return null;
    }

}
