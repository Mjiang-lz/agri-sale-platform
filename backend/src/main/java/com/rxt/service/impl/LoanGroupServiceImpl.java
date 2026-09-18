package com.rxt.service.impl;

import com.rxt.dto.LoanGroupDTO;
import com.rxt.service.LoanGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanGroupServiceImpl implements LoanGroupService {

    @Override
    public void createGroup(Long userId, LoanGroupDTO dto) {
        // 1. 校验用户是否有资格建组
        // 2. 插入 t_loan_group 表
    }

    @Override
    @Transactional // ⚠️ 团队红线：涉及多表写入，必须加事务防并发超员！
    public void joinGroup(Long groupId, Long userId) {
        // 1. 查小组当前人数，校验是否达到上限
        // 2. 校验用户是否已在其他组
        // 3. 插入组员记录
    }

    @Override
    public void quitGroup(Long groupId, Long userId) {
        // 1. 校验用户是否在组内
        // 2. 删除组员记录
    }
}