package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.common.BizException;
import com.rxt.dto.FinanceApplyDTO;
import com.rxt.entity.Approval;
import com.rxt.entity.FarmerProfile;
import com.rxt.entity.FinanceApply;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.ApprovalMapper;
import com.rxt.mapper.FarmerProfileMapper;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.mapper.LoanGroupMapper;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.security.UserContext;
import com.rxt.service.FinanceApplyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rxt.vo.GuarantorCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 融资申请业务实现。
 *
 * <p>角色口径（《软件需求规格说明书》2.5 融资服务、2.7 融资管理）：
 * 融资申请、我的申请、联合融资人推荐为农户独有动作；银行侧审批为银行用户独有动作。
 * 各方法进入业务逻辑前先做角色校验，非对应角色一律 403。</p>
 */
@Service // ⚠️ 极其重要：加上这个注解，Controller 才能找到它！
@RequiredArgsConstructor
public class FinanceApplyServiceImpl extends ServiceImpl<FinanceApplyMapper, FinanceApply> implements FinanceApplyService {

    // 智能推荐的特征权重。五项相加 = 100，即「五项全中」为满配。
    // 地区与品类给得最重：联保小组线下要互相担保、共同履约，同产业带、同区域
    // 的农户才有现实可行性；额度与期限是银行侧看重的准入条件，次之。
    private static final int WEIGHT_CATEGORY = 30;
    private static final int WEIGHT_CATEGORY_MAJOR = 20;
    private static final int WEIGHT_REGION = 35;
    private static final int WEIGHT_AMOUNT = 15;
    private static final int WEIGHT_TERM = 10;
    private static final int WEIGHT_YEARS = 10;

    private final PermissionChecker permissionChecker;

    /** 智能推荐的输入：候选农户的经营档案 */
    private final FarmerProfileMapper farmerProfileMapper;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    /** 用于「过滤已组队关系」：取当前处在进行中小组里的农户 */
    private final LoanGroupMapper loanGroupMapper;

    @Autowired
    private ApprovalMapper approvalMapper;

    @Override
    @Transactional // ⚠️ 团队红线：涉及写库，必须加事务
    public void submitApply(Long userId, FinanceApplyDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        FinanceApply apply = new FinanceApply();
        apply.setApplyNo(generateApplyNo());
        // ⚠️ 极其关键：完全按照 FinanceApply.java 里的真实字段名修改！
        apply.setFarmerId(userId);                        // 实体类里叫 farmerId，不是 userId
        apply.setLoanProductId(dto.getLoanProductId());   // 实体类里叫 loanProductId
        apply.setApplyAmount(dto.getApplyAmount());       // 实体类里叫 applyAmount
        apply.setApplyTerm(dto.getApplyTerm());           // 实体类里叫 applyTerm
        apply.setApplyPurpose(dto.getApplyPurpose());     // 实体类里叫 applyPurpose

        apply.setApplyStatus("待匹配");                   // 实体类里叫 applyStatus
        apply.setCreateTime(LocalDateTime.now());
        this.save(apply);
    }

    @Override
    public Page<FinanceApply> getMyApply(Long userId, Integer page, Integer size) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        LambdaQueryWrapper<FinanceApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceApply::getFarmerId, userId)
                .orderByDesc(FinanceApply::getApplyTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    /**
     * 联合融资人智能推荐（需求规格说明书 REQ-07）。
     *
     * <p><b>数据来源：</b>{@code t_farmer_profile.credit_record} 里按约定记着
     * 「经营品类 / 地区 / 意向额度 / 意向期限 / 经营年限」五项特征
     * （格式见 {@code db/data.sql} 第 3 节），本方法把它们解析出来后与申请人逐项比对。
     * 这是需求里写的「按特征相似度排序」的落地方式 —— 表里没有任何数值化的特征列，
     * 可用的特征就只有这一段结构化文本。</p>
     *
     * <p><b>返回绝对分而不是集合内相对排名：</b>如果按候选集合归一化，
     * 一个只有两三个农户的小库里必然出现「100% 匹配」，哪怕两人品类、地区、
     * 额度全都不同。绝对分才经得起「凭什么说 92%」的追问。</p>
     *
     * <p><b>已组队的农户直接不出现在列表里</b>（REQ-07 要求「过滤已组队关系」），
     * 而不是排在后面 —— 推荐一个已经在小队里的人，点下去必然失败。</p>
     */
    @Override
    public List<GuarantorCandidateVO> recommendGuarantors(Long userId) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        FarmerProfile self = farmerProfileMapper.selectOne(Wrappers.<FarmerProfile>lambdaQuery()
                .eq(FarmerProfile::getUserId, userId)
                .last("LIMIT 1"));
        if (self == null) {
            // 没有经营档案就没有可比的特征。此时返回空列表会让人读成
            // 「平台里没有别的农户」，与真实原因（缺档案）完全不是一回事。
            throw new BizException("请先完善农户经营档案（主营产品与经营规模），系统才能按经营特征匹配联保人");
        }
        FarmerFeature selfFeature = FarmerFeature.of(self);

        Long farmerRoleId = farmerRoleId();
        if (farmerRoleId == null) {
            return new ArrayList<>();
        }

        List<User> farmers = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getRoleId, farmerRoleId)
                .eq(User::getStatus, 1)
                .ne(User::getUserId, userId)
                .orderByAsc(User::getUserId));
        if (farmers.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> alreadyGrouped = new HashSet<>(loanGroupMapper.selectActiveMemberFarmerIds());

        // 候选人的档案一次批量取回，不在循环里逐个查库（N+1）
        Set<Long> candidateIds = farmers.stream().map(User::getUserId).collect(Collectors.toSet());
        Map<Long, FarmerProfile> profiles = farmerProfileMapper.selectList(
                        Wrappers.<FarmerProfile>lambdaQuery().in(FarmerProfile::getUserId, candidateIds))
                .stream()
                .collect(Collectors.toMap(FarmerProfile::getUserId, Function.identity(), (a, b) -> a));

        List<GuarantorCandidateVO> candidates = new ArrayList<>();
        for (User farmer : farmers) {
            if (alreadyGrouped.contains(farmer.getUserId())) {
                continue;
            }

            GuarantorCandidateVO vo = new GuarantorCandidateVO();
            vo.setUserId(farmer.getUserId());
            vo.setRealName(farmer.getRealName());
            vo.setAvatarUrl(farmer.getAvatarUrl());

            FarmerProfile profile = profiles.get(farmer.getUserId());
            if (profile == null) {
                vo.setMatchRate(0);
                vo.setMatchReason("该农户尚未完善经营档案，无法比对经营特征，可作为补充人选");
                candidates.add(vo);
                continue;
            }

            vo.setMainProduct(profile.getMainProduct());
            vo.setScaleDesc(profile.getScaleDesc());

            FarmerFeature feature = FarmerFeature.of(profile);
            vo.setRegion(feature.region());
            MatchScore score = MatchScore.between(selfFeature, feature);
            vo.setMatchRate(score.total());
            vo.setMatchReason(score.reason());
            candidates.add(vo);
        }

        candidates.sort(Comparator
                .comparingInt((GuarantorCandidateVO c) -> c.getMatchRate() == null ? 0 : c.getMatchRate())
                .reversed()
                .thenComparing(GuarantorCandidateVO::getUserId));
        return candidates;
    }

    /** 农户角色的主键。查不到时返回 null，调用方需按「没有候选人」处理 */
    private Long farmerRoleId() {
        Role farmerRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleName, RoleConstant.FARMER)
                .last("LIMIT 1"));
        return farmerRole == null ? null : farmerRole.getRoleId();
    }

    // -------------------------------------------------------------------------
    // 特征解析与打分：只服务于推荐，故作为私有实现细节放在本类内
    // -------------------------------------------------------------------------

    /** 从 credit_record 里取某个键的值，形如 {@code 地区：江西赣州；} */
    private static String segment(String record, String key) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        // 分隔符「：」在种子数据里有，但「经营年限 8 年」这种只有空格的写法也存在，
        // 所以冒号设为可选，避免某一项永远解析不出来（那项就会恒不参与打分）
        Matcher matcher = Pattern.compile(key + "\\s*[:：]?\\s*([^；;，,]+)").matcher(record);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** 金额文本转数值：「10 万元」→ 100000，「80000 元」→ 80000 */
    private static BigDecimal decimalOf(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        return raw.contains("万") ? value.multiply(BigDecimal.valueOf(10000)) : value;
    }

    /** 取文本里的第一个整数：「12 个月」→ 12 */
    private static Integer intOf(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(raw);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 农户经营特征。任一项解析不到即为 null，该项不参与打分 */
    private record FarmerFeature(String categoryMajor, String categoryMinor, String region,
                                 BigDecimal amount, Integer term, Integer years) {

        static FarmerFeature of(FarmerProfile profile) {
            String record = profile.getCreditRecord();
            String category = segment(record, "经营品类");
            if (category == null || category.isBlank()) {
                // 档案里没写经营品类时退回主营产品，至少还能比出「同品类」
                category = profile.getMainProduct();
            }

            String major = null;
            String minor = null;
            if (category != null && !category.isBlank()) {
                String trimmed = category.trim();
                int dash = trimmed.indexOf('-');
                if (dash > 0) {
                    major = trimmed.substring(0, dash).trim();
                    minor = trimmed.substring(dash + 1).trim();
                } else {
                    major = trimmed;
                    minor = trimmed;
                }
            }

            return new FarmerFeature(major, minor,
                    segment(record, "地区"),
                    decimalOf(segment(record, "意向额度")),
                    intOf(segment(record, "意向期限")),
                    intOf(segment(record, "经营年限")));
        }
    }

    /** 匹配度与理由 */
    private record MatchScore(int total, String reason) {

        static MatchScore between(FarmerFeature self, FarmerFeature other) {
            int total = 0;
            List<String> hits = new ArrayList<>();

            if (self.categoryMajor() != null && other.categoryMajor() != null) {
                boolean sameMinor = self.categoryMinor() != null
                        && self.categoryMinor().equals(other.categoryMinor());
                if (sameMinor) {
                    total += WEIGHT_CATEGORY;
                    hits.add("同属" + self.categoryMinor() + "经营");
                } else if (self.categoryMajor().equals(other.categoryMajor())) {
                    total += WEIGHT_CATEGORY_MAJOR;
                    hits.add("同属" + self.categoryMajor() + "大类");
                }
            }

            if (self.region() != null && self.region().equals(other.region())) {
                total += WEIGHT_REGION;
                hits.add("同地区（" + self.region() + "）");
            }

            if (self.amount() != null && other.amount() != null
                    && self.amount().signum() > 0 && other.amount().signum() > 0) {
                // 用较大者作分母：额度差异是相对的，10 万对 12 万与 100 万对 120 万应同分
                BigDecimal gap = self.amount().subtract(other.amount()).abs()
                        .divide(self.amount().max(other.amount()), 4, RoundingMode.HALF_UP);
                if (gap.compareTo(new BigDecimal("0.2")) <= 0) {
                    total += WEIGHT_AMOUNT;
                    hits.add("意向额度接近");
                } else if (gap.compareTo(new BigDecimal("0.5")) <= 0) {
                    total += WEIGHT_AMOUNT / 2;
                }
            }

            if (self.term() != null && self.term().equals(other.term())) {
                total += WEIGHT_TERM;
                hits.add("意向期限一致");
            }

            if (self.years() != null && other.years() != null
                    && Math.abs(self.years() - other.years()) <= 3) {
                total += WEIGHT_YEARS;
                hits.add("经营年限接近");
            }

            String reason = hits.isEmpty()
                    ? "经营特征与你的差异较大，本次没有命中项，可作为补充人选"
                    : "命中的经营特征：" + String.join("、", hits);
            return new MatchScore(Math.min(total, 100), reason);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void bankApprove(Long applyId, String approvalResult, BigDecimal loanAmount, String approvalOpinion) {
        // 这是全项目唯一能把申请改写成「已放款」的动作，必须最先挡住非银行用户
        permissionChecker.requireRole(RoleConstant.BANK);

        FinanceApply apply = this.getById(applyId);
        if (apply == null) {
            throw new RuntimeException("融资申请不存在");
        }

        apply.setApplyStatus("通过".equals(approvalResult) ? "已放款" : "审批未通过");
        this.updateById(apply);

        Approval approval = new Approval();
        approval.setApplyId(applyId);
        approval.setBankUserId(UserContext.currentUserId());
        approval.setApprovalResult(approvalResult);
        approval.setLoanAmount(loanAmount);
        approval.setApprovalOpinion(approvalOpinion);
        approval.setApprovalTime(LocalDateTime.now());

        approvalMapper.insert(approval);
    }

    private String generateApplyNo() {
        return "FA" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
