package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.entity.Approval;
import com.rxt.entity.FarmerProfile;
import com.rxt.entity.FinanceApply;
import com.rxt.entity.LoanProduct;
import com.rxt.entity.User;
import com.rxt.mapper.ApprovalMapper;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.LoanProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.vo.FinanceApplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 融资申请视图装配器（银行侧与管理员台账共用）。
 *
 * <p>融资申请列表要把农户、贷款产品、（可选）审批结论拼进来。这段拼装逻辑
 * 银行侧与管理员台账两边都需要，抽到这里避免两处各写一遍四表关联。</p>
 *
 * <p>全部按 {@code IN} 批量取数后再在内存里做映射，不做逐行查库 ——
 * 列表页逐行拼装会退化成 N+1 查询。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApplyAssembler {

    private final UserMapper userMapper;
    private final LoanProductMapper loanProductMapper;
    private final FarmerProfileMapper farmerProfileMapper;
    private final ApprovalMapper approvalMapper;

    /**
     * 批量装配。
     *
     * @param applies      融资申请实体列表
     * @param withApproval 是否附带审批结论与农户经营档案。
     *                     农户侧「我的融资」不需要，传 false 可以少两次查询。
     */
    public List<FinanceApplyVO> assemble(List<FinanceApply> applies, boolean withApproval) {
        if (applies == null || applies.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> farmerIds = applies.stream()
                .map(FinanceApply::getFarmerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> productIds = applies.stream()
                .map(FinanceApply::getLoanProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, User> farmers = farmerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(farmerIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));
        Map<Long, String> productNames = productIds.isEmpty() ? Collections.emptyMap()
                : loanProductMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(LoanProduct::getLoanProductId,
                                LoanProduct::getProductName, (a, b) -> a));

        Map<Long, FarmerProfile> profiles = Collections.emptyMap();
        Map<Long, Approval> approvals = Collections.emptyMap();
        if (withApproval) {
            profiles = farmerIds.isEmpty() ? Collections.emptyMap()
                    : farmerProfileMapper.selectList(Wrappers.<FarmerProfile>lambdaQuery()
                            .in(FarmerProfile::getUserId, farmerIds)).stream()
                            .collect(Collectors.toMap(FarmerProfile::getUserId, Function.identity(), (a, b) -> a));
            approvals = loadLatestApprovals(applies);
        }

        Map<Long, User> finalFarmers = farmers;
        Map<Long, FarmerProfile> finalProfiles = profiles;
        Map<Long, Approval> finalApprovals = approvals;

        return applies.stream()
                .map(apply -> toVO(apply, finalFarmers, productNames, finalProfiles, finalApprovals))
                .collect(Collectors.toList());
    }

    /**
     * 取每条申请最新的一条审批记录。
     *
     * <p>表上没有对 apply_id 建唯一约束（只建了普通索引），因此按 approval_id
     * 取最大的一条，保证重复审批时列表展示的是最新结论。</p>
     */
    private Map<Long, Approval> loadLatestApprovals(List<FinanceApply> applies) {
        Set<Long> applyIds = applies.stream()
                .map(FinanceApply::getApplyId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (applyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Approval> list = approvalMapper.selectList(Wrappers.<Approval>lambdaQuery()
                .in(Approval::getApplyId, applyIds)
                .orderByAsc(Approval::getApprovalId));
        Map<Long, Approval> latest = new HashMap<>();
        for (Approval approval : list) {
            // 升序遍历，后到的覆盖先到的，最终留下 approval_id 最大的那条
            latest.put(approval.getApplyId(), approval);
        }
        return latest;
    }

    private FinanceApplyVO toVO(FinanceApply apply,
                               Map<Long, User> farmers,
                               Map<Long, String> productNames,
                               Map<Long, FarmerProfile> profiles,
                               Map<Long, Approval> approvals) {
        FinanceApplyVO vo = new FinanceApplyVO();
        vo.setApplyId(apply.getApplyId());
        vo.setApplyNo(apply.getApplyNo());
        vo.setApplyAmount(apply.getApplyAmount());
        vo.setApplyTerm(apply.getApplyTerm());
        vo.setApplyStatus(apply.getApplyStatus());
        vo.setApplyTime(apply.getApplyTime());
        vo.setApplyPurpose(apply.getApplyPurpose());
        vo.setLoanProductId(apply.getLoanProductId());
        vo.setPushTime(apply.getPushTime());
        vo.setFarmerId(apply.getFarmerId());

        User farmer = apply.getFarmerId() == null ? null : farmers.get(apply.getFarmerId());
        if (farmer != null) {
            vo.setFarmerName(farmer.getRealName());
            vo.setFarmerPhone(farmer.getPhone());
        }
        vo.setLoanProductName(productNames.get(apply.getLoanProductId()));

        FarmerProfile profile = apply.getFarmerId() == null ? null : profiles.get(apply.getFarmerId());
        if (profile != null) {
            vo.setFarmerMainProduct(profile.getMainProduct());
        }

        Approval approval = apply.getApplyId() == null ? null : approvals.get(apply.getApplyId());
        if (approval != null) {
            vo.setApprovalResult(approval.getApprovalResult());
            vo.setLoanAmount(approval.getLoanAmount());
            vo.setApprovalOpinion(approval.getApprovalOpinion());
            vo.setApprovalTime(approval.getApprovalTime());
        }
        return vo;
    }
}
