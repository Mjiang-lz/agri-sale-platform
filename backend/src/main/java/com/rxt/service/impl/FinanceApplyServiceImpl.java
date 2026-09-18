package com.rxt.service.impl;

import com.rxt.dto.FinanceApplyDTO;
import com.rxt.entity.FinanceApply;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.service.FinanceApplyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rxt.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service // ⚠️ 极其重要：加上这个注解，Controller 才能找到它！
public class FinanceApplyServiceImpl extends ServiceImpl<FinanceApplyMapper, FinanceApply> implements FinanceApplyService {

    @Override
    @Transactional // ⚠️ 团队红线：涉及写库，必须加事务
    public void submitApply(Long userId, FinanceApplyDTO dto) {
        FinanceApply apply = new FinanceApply();

        // ⚠️ 极其关键：完全按照 FinanceApply.java 里的真实字段名修改！
        apply.setFarmerId(userId);                        // 实体类里叫 farmerId，不是 userId
        apply.setLoanProductId(dto.getLoanProductId());   // 实体类里叫 loanProductId
        apply.setApplyAmount(dto.getApplyAmount());       // 实体类里叫 applyAmount
        apply.setApplyTerm(dto.getApplyTerm());           // 实体类里叫 applyTerm
        apply.setApplyPurpose(dto.getApplyPurpose());     // 实体类里叫 applyPurpose

        apply.setApplyStatus("待审核");                    // 实体类里叫 applyStatus
        apply.setCreateTime(LocalDateTime.now());
        this.save(apply);
    }

    @Override
    public Page<FinanceApply> getMyApply(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<FinanceApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceApply::getFarmerId, userId)
                .orderByDesc(FinanceApply::getApplyTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<UserVO> recommendGuarantors(Long userId) {
        return new ArrayList<>();
    }
}