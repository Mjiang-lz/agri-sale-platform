package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.entity.FinanceApply;
import com.rxt.entity.User;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.service.AdminFinanceService;
import com.rxt.vo.FinanceApplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 融资台账业务实现（M6 平台治理）。
 *
 * <p>只读。管理员在这里核对融资流转情况（谁申请、什么状态、银行出了什么结论），
 * 审批动作仍归银行用户，见 {@link LoanAdminServiceImpl}。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminFinanceServiceImpl implements AdminFinanceService {

    private final PermissionChecker permissionChecker;
    private final FinanceApplyMapper financeApplyMapper;
    private final UserMapper userMapper;
    private final FinanceApplyAssembler financeApplyAssembler;

    @Override
    public IPage<FinanceApplyVO> pageApplies(String applyStatus, String keyword,
                                             long pageNum, long pageSize) {
        permissionChecker.requireAdmin();

        LambdaQueryWrapper<FinanceApply> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(applyStatus)) {
            wrapper.eq(FinanceApply::getApplyStatus, applyStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 关键字可能落在两个对象上：申请编号在 t_finance_apply，农户姓名在 t_user。
            // 先按姓名把农户主键捞出来，再并进同一个条件，避免为了一个搜索框写手写 SQL 关联。
            List<Long> farmerIds = userMapper.selectList(
                            Wrappers.<User>lambdaQuery().like(User::getRealName, kw))
                    .stream().map(User::getUserId).collect(Collectors.toList());
            wrapper.and(q -> {
                q.like(FinanceApply::getApplyNo, kw);
                if (!farmerIds.isEmpty()) {
                    q.or().in(FinanceApply::getFarmerId, farmerIds);
                }
            });
        }
        wrapper.orderByDesc(FinanceApply::getApplyId);

        Page<FinanceApply> page = financeApplyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<FinanceApplyVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(financeApplyAssembler.assemble(page.getRecords(), true));
        return result;
    }
}
