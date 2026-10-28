package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.LoanGroupDTO;
import com.rxt.entity.FinanceApply;
import com.rxt.entity.LoanGroup;
import com.rxt.entity.LoanProduct;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.mapper.LoanGroupMapper;
import com.rxt.mapper.LoanProductMapper;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.security.RoleConstant;
import com.rxt.service.LoanGroupService;
import com.rxt.vo.LoanGroupCreateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 联合贷款小组业务实现（REQ-08）。
 *
 * <p>联保小组只可能由农户组成（需求规格说明书 2.5 融资服务：联合贷款小组组建），
 * 因此三个动作进入业务逻辑前先做角色校验，非农户一律 403。</p>
 *
 * <h3>成员关系怎么表示</h3>
 * <p>按 {@code db/data.sql} 第 15 节确定的设计，<b>不单独建成员表</b>：
 * 「某人在某个小组里」= 他有一条 {@code t_finance_apply.group_id} 指向该小组的融资申请。
 * 由此推出两条硬性约束，实现时必须一并满足，否则成员关系会自相矛盾：</p>
 * <ol>
 *   <li>想入组，<b>手里必须先有一笔该产品的「待匹配」申请</b> ——
 *       没有申请就没有「载体」可以挂 {@code group_id}。这也是「联保＝多户共同申请」
 *       业务模型的自然结果，不是实现上的将就。</li>
 *   <li>{@code t_loan_group.member_count} / {@code target_amount} 是<b>冗余汇总列</b>，
 *       必须与成员申请记录在<b>同一个事务</b>里一起更新，否则两者会漂移。</li>
 * </ol>
 *
 * <h3>并发</h3>
 * <p>加入小组是「先数人数、再落记录」，两步间存在竞态（团队红线：防并发超员）。
 * {@code @Transactional} 本身挡不住 —— MySQL 默认 REPEATABLE READ 不给普通读加锁，
 * 两个并发事务会读到同一个人数快照。因此所有会改动人数的入口都先用
 * {@code selectByIdForUpdate} 把小组行<b>锁住</b>，让后来者排队，
 * 读到的就是已提交后的新人数。</p>
 */
@Service
@RequiredArgsConstructor
public class LoanGroupServiceImpl implements LoanGroupService {

    /** 小组可变动的状态：仅此状态下接受加入 / 退出 */
    private static final String GROUP_STATUS_FORMING = "组建中";

    /** 融资申请的初始状态，也是退出小组后回退到的状态 */
    private static final String APPLY_STATUS_PENDING = "待匹配";

    /** 成员并入小组后融资申请的状态 */
    private static final String APPLY_STATUS_GROUPED = "已组建小组";

    private static final String MEMBER_ROLE_LEADER = "组长";
    private static final String MEMBER_ROLE_MEMBER = "成员";

    /** t_loan_group.group_name 是 VARCHAR(50) */
    private static final int GROUP_NAME_MAX_LENGTH = 50;

    /** 兜底起贷人数：产品未配 min_group_size 时按 2 户处理（1 户即为个体贷款） */
    private static final int DEFAULT_MIN_GROUP_SIZE = 2;

    private final PermissionChecker permissionChecker;
    private final LoanGroupMapper loanGroupMapper;
    private final FinanceApplyMapper financeApplyMapper;
    private final LoanProductMapper loanProductMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    // =====================================================================
    // 建组
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoanGroupCreateVO createGroup(Long userId, LoanGroupDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        // 1. 定位发起人本次组队对应的融资申请。没有申请就建组，
        //    「目标融资金额」「起贷人数」都无从取得，小组会是一个空壳。
        FinanceApply leaderApply = findPendingApply(userId,
                dto == null ? null : dto.getLoanProductId());
        if (leaderApply == null) {
            throw new BizException("请先提交融资申请，再组建联合贷款小组");
        }

        LoanProduct product = loanProductMapper.selectById(leaderApply.getLoanProductId());
        if (product == null) {
            throw new BizException("该融资申请对应的贷款产品不存在");
        }
        int minGroupSize = resolveMinGroupSize(product);

        // 2. 个体贷款产品没有联保的必要：小组会只有发起人一人，
        //    建出来既进不了第二个人，也没有任何业务含义。
        if (minGroupSize <= 1) {
            throw new BizException(product.getProductName() + " 为个体贷款产品，无需组建联保小组");
        }

        // 3. 发起人不能已经在别的进行中小组里（同时最多参与一个）
        if (loanGroupMapper.countActiveMembership(userId) > 0) {
            throw new BizException("你已在一个进行中的联合贷款小组中，不能重复组建");
        }

        // 4. 落小组主记录。member_count / target_amount 先按「只有组长」写，
        //    待所有成员并入后再与实际成员记录一起回写。
        LoanGroup group = new LoanGroup();
        group.setGroupName(resolveGroupName(dto == null ? null : dto.getGroupName(), product));
        group.setLeaderId(userId);
        group.setTargetAmount(nullToZero(leaderApply.getApplyAmount()));
        group.setMemberCount(1);
        group.setGroupStatus(GROUP_STATUS_FORMING);
        loanGroupMapper.insert(group);

        LoanGroupCreateVO vo = new LoanGroupCreateVO();
        vo.setGroupId(group.getGroupId());
        vo.setGroupName(group.getGroupName());
        vo.setLeaderId(userId);
        vo.setMinGroupSize(minGroupSize);
        vo.setGroupStatus(GROUP_STATUS_FORMING);

        // 5. 组长自己的申请并组
        User leader = userMapper.selectById(userId);
        mergeIntoGroup(leaderApply, group.getGroupId());
        vo.getJoined().add(toMember(leaderApply, userId,
                leader == null ? null : leader.getRealName(), MEMBER_ROLE_LEADER));

        int memberCount = 1;
        BigDecimal targetAmount = nullToZero(leaderApply.getApplyAmount());

        // 农户角色主键只查一次：候选人逐个校验时复用它，避免每人一次角色表查询（N+1）
        Long farmerRoleId = farmerRoleId();

        // 6. 逐个并入被推荐的候选人。任何一个人进不来都<b>不能</b>让整个建组失败
        //    （组长的组队动作本身是合法的），但也不能悄悄跳过 —— 逐一记入 skipped 回传。
        for (Long memberId : distinctCandidateIds(dto == null ? null : dto.getMemberIds(), userId)) {
            User candidate = userMapper.selectById(memberId);
            if (candidate == null) {
                skip(vo, memberId, null, "该用户不存在");
                continue;
            }
            String candidateName = candidate.getRealName();

            if (!isEnabledFarmer(candidate, farmerRoleId)) {
                skip(vo, memberId, candidateName, "该账号不是可用的农户账号，无法参与联保");
                continue;
            }
            if (memberCount >= minGroupSize) {
                skip(vo, memberId, candidateName,
                        "小组人数已达上限（" + minGroupSize + " 户），本次未并入");
                continue;
            }
            if (loanGroupMapper.countActiveMembership(memberId) > 0) {
                skip(vo, memberId, candidateName, "该农户已在其他进行中的联合贷款小组中");
                continue;
            }

            FinanceApply candidateApply = findPendingApply(memberId, leaderApply.getLoanProductId());
            if (candidateApply == null) {
                skip(vo, memberId, candidateName,
                        "该农户尚未提交「" + product.getProductName() + "」的融资申请，暂不能入组");
                continue;
            }

            mergeIntoGroup(candidateApply, group.getGroupId());
            memberCount++;
            targetAmount = targetAmount.add(nullToZero(candidateApply.getApplyAmount()));
            vo.getJoined().add(toMember(candidateApply, memberId, candidateName, MEMBER_ROLE_MEMBER));
        }

        // 7. 冗余汇总列与成员记录同事务回写
        group.setMemberCount(memberCount);
        group.setTargetAmount(targetAmount);
        loanGroupMapper.updateById(group);

        vo.setMemberCount(memberCount);
        vo.setTargetAmount(targetAmount);
        return vo;
    }

    // =====================================================================
    // 加入
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinGroup(Long groupId, Long userId) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        // 先锁小组行：本方法下文所有判断都建立在「人数」之上，必须先拿到独占快照
        LoanGroup group = requireLockedGroup(groupId);
        requireForming(group);

        if (userId.equals(group.getLeaderId())) {
            throw new BizException("你已是该小组组长，无需重复加入");
        }

        int minGroupSize = resolveMinGroupSizeByGroup(group);
        long currentCount = countMembers(groupId);
        if (currentCount >= minGroupSize) {
            throw new BizException("小组人数已达上限（" + minGroupSize + " 户），不能再加入");
        }

        if (loanGroupMapper.countActiveMembership(userId) > 0) {
            throw new BizException("你已在其他进行中的联合贷款小组中，同时最多参与一个");
        }

        Long productId = leaderProductId(group);
        FinanceApply apply = findPendingApply(userId, productId);
        if (apply == null) {
            throw new BizException("请先提交相同贷款产品的融资申请，再加入该联合贷款小组");
        }

        mergeIntoGroup(apply, groupId);
        group.setMemberCount((int) currentCount + 1);
        group.setTargetAmount(nullToZero(group.getTargetAmount()).add(nullToZero(apply.getApplyAmount())));
        loanGroupMapper.updateById(group);
    }

    // =====================================================================
    // 退出 / 解散
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void quitGroup(Long groupId, Long userId) {
        permissionChecker.requireRole(RoleConstant.FARMER);

        LoanGroup group = requireLockedGroup(groupId);
        requireForming(group);

        FinanceApply mine = findGroupedApply(groupId, userId);
        if (mine == null) {
            throw new BizException("你不在该小组中");
        }

        if (userId.equals(group.getLeaderId())) {
            // 组长退出 = 解散：小组不能没有组长，其余成员一并退回「待匹配」，
            // 让他们可以重新组队，而不是被挂在一个已无人负责的小组里。
            releaseAllMembers(groupId);
            loanGroupMapper.deleteById(groupId);
            return;
        }

        releaseFromGroup(mine);
        long remaining = countMembers(groupId);
        group.setMemberCount((int) remaining);
        group.setTargetAmount(nullToZero(group.getTargetAmount())
                .subtract(nullToZero(mine.getApplyAmount())));
        loanGroupMapper.updateById(group);
    }

    // =====================================================================
    // 私有辅助
    // =====================================================================

    /**
     * 取小组并加行锁；不存在直接抛 404。
     *
     * <p>只能在事务内调用 —— 自动提交下 {@code FOR UPDATE} 的锁会立即释放，
     * 锁不住任何东西。</p>
     */
    private LoanGroup requireLockedGroup(Long groupId) {
        LoanGroup group = loanGroupMapper.selectByIdForUpdate(groupId);
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "联合贷款小组不存在");
        }
        return group;
    }

    /** 只有「组建中」的小组允许成员变动 */
    private void requireForming(LoanGroup group) {
        if (!GROUP_STATUS_FORMING.equals(group.getGroupStatus())) {
            throw new BizException("该小组当前状态为「" + group.getGroupStatus()
                    + "」，成员名单已冻结，不能再变动");
        }
    }

    /**
     * 找某人某产品的「待匹配」申请。
     *
     * <p>按 {@code apply_id} 倒序取最新一条：同一农户对同一产品可能提交过多笔
     * （驳回后重新申请），应当并入最近这笔。</p>
     *
     * @param loanProductId 为 null 表示不限产品
     */
    private FinanceApply findPendingApply(Long farmerId, Long loanProductId) {
        return financeApplyMapper.selectOne(Wrappers.<FinanceApply>lambdaQuery()
                .eq(FinanceApply::getFarmerId, farmerId)
                .eq(FinanceApply::getApplyStatus, APPLY_STATUS_PENDING)
                .isNull(FinanceApply::getGroupId)
                // 条件在 last() 之前拼好：last() 会把 LIMIT 直接接到 SQL 尾部，
                // 之后再追加条件会生成 "… LIMIT 1 AND loan_product_id = ?" 这种非法 SQL
                .eq(loanProductId != null, FinanceApply::getLoanProductId, loanProductId)
                .orderByDesc(FinanceApply::getApplyId)
                .last("LIMIT 1"));
    }

    /** 取某人在指定小组里的融资申请（即他的成员身份载体） */
    private FinanceApply findGroupedApply(Long groupId, Long farmerId) {
        return financeApplyMapper.selectOne(Wrappers.<FinanceApply>lambdaQuery()
                .eq(FinanceApply::getGroupId, groupId)
                .eq(FinanceApply::getFarmerId, farmerId)
                .last("LIMIT 1"));
    }

    /** 统计小组实际成员数。以成员申请记录为准，而不是冗余列 member_count */
    private long countMembers(Long groupId) {
        Long count = financeApplyMapper.selectCount(Wrappers.<FinanceApply>lambdaQuery()
                .eq(FinanceApply::getGroupId, groupId));
        return count == null ? 0L : count;
    }

    /** 把成员的申请并组：回填 group_id 并推进入组状态 */
    private void mergeIntoGroup(FinanceApply apply, Long groupId) {
        apply.setGroupId(groupId);
        apply.setApplyStatus(APPLY_STATUS_GROUPED);
        financeApplyMapper.updateById(apply);
    }

    /**
     * 把成员申请退回「待匹配」并解绑小组。
     *
     * <p><b>必须用 {@code LambdaUpdateWrapper} 而不是 {@code updateById}：</b>
     * MyBatis-Plus 的 {@code updateById} 默认只更新非 null 字段（{@code FieldStrategy.NOT_NULL}），
     * 把 {@code groupId} 置为 null 的写法会被<b>静默忽略</b> ——
     * 接口返回成功、申请状态改了、却仍然挂在这个小组里，退组等于没退。
     * 要清空一个字段，只能用 update wrapper 显式 {@code set(column, null)}。</p>
     */
    private void releaseFromGroup(FinanceApply apply) {
        financeApplyMapper.update(null, Wrappers.<FinanceApply>lambdaUpdate()
                .eq(FinanceApply::getApplyId, apply.getApplyId())
                .set(FinanceApply::getGroupId, null)
                .set(FinanceApply::getApplyStatus, APPLY_STATUS_PENDING));
    }

    /** 解散小组：把所有成员退回「待匹配」，只动确实由本组并进去的那些记录 */
    private void releaseAllMembers(Long groupId) {
        financeApplyMapper.update(null, Wrappers.<FinanceApply>lambdaUpdate()
                .eq(FinanceApply::getGroupId, groupId)
                .eq(FinanceApply::getApplyStatus, APPLY_STATUS_GROUPED)
                .set(FinanceApply::getGroupId, null)
                .set(FinanceApply::getApplyStatus, APPLY_STATUS_PENDING));
    }

    /** 由小组反查组长那笔申请的产品，从而拿到起贷人数 */
    private int resolveMinGroupSizeByGroup(LoanGroup group) {
        Long productId = leaderProductId(group);
        if (productId == null) {
            return DEFAULT_MIN_GROUP_SIZE;
        }
        LoanProduct product = loanProductMapper.selectById(productId);
        return product == null ? DEFAULT_MIN_GROUP_SIZE : resolveMinGroupSize(product);
    }

    /** 取组长在指定小组里的申请所属贷款产品 */
    private Long leaderProductId(LoanGroup group) {
        if (group.getLeaderId() == null) {
            return null;
        }
        FinanceApply leaderApply = findGroupedApply(group.getGroupId(), group.getLeaderId());
        return leaderApply == null ? null : leaderApply.getLoanProductId();
    }

    /**
     * 起贷人数下限。
     *
     * <p>{@code min_group_size} 同时也是<b>人数上限</b>：产品没有配「最大组队人数」这一列，
     * 而联合贷款的可贷总额（{@code max_amount}）＝起贷人数 × 单户额度，
     * 凑满起贷人数即封组是与现有数据自洽的读法。若将来要支持「超过起贷人数继续加入」，
     * 应先在 t_loan_product 上补一列 max_group_size，不要把上限硬编码在这里。</p>
     */
    private int resolveMinGroupSize(LoanProduct product) {
        Integer configured = product.getMinGroupSize();
        return configured == null || configured < 1 ? DEFAULT_MIN_GROUP_SIZE : configured;
    }

    /** 农户角色主键，查不到返回 null（此时任何候选人都判为「非农户」） */
    private Long farmerRoleId() {
        Role farmerRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleName, RoleConstant.FARMER)
                .last("LIMIT 1"));
        return farmerRole == null ? null : farmerRole.getRoleId();
    }

    /** 该用户是否为启用状态的农户 */
    private boolean isEnabledFarmer(User user, Long farmerRoleId) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        return farmerRoleId != null && farmerRoleId.equals(user.getRoleId());
    }

    /**
     * 候选人列表去重、剔除 null 与组长自己（组长已在组内，重复处理会撞「已在组内」）。
     *
     * <p>用 {@link java.util.LinkedHashSet} 而不是 HashSet：保留前端勾选顺序，
     * 人数触顶时被挡下的是排在后面的人，与用户看到的顺序一致。</p>
     */
    private Set<Long> distinctCandidateIds(List<Long> memberIds, Long leaderId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return ids;
        }
        for (Long id : memberIds) {
            if (id != null && !id.equals(leaderId)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 组名：前端传了就用前端的，否则按产品名自动生成；都要截到列宽以内 */
    private String resolveGroupName(String requested, LoanProduct product) {
        String name = requested == null ? "" : requested.trim();
        if (name.isEmpty()) {
            name = product.getProductName() + "联合小组";
        }
        return name.length() > GROUP_NAME_MAX_LENGTH ? name.substring(0, GROUP_NAME_MAX_LENGTH) : name;
    }

    private LoanGroupCreateVO.Member toMember(FinanceApply apply, Long userId,
                                              String realName, String memberRole) {
        LoanGroupCreateVO.Member member = new LoanGroupCreateVO.Member();
        member.setUserId(userId);
        member.setRealName(realName);
        member.setApplyAmount(apply.getApplyAmount());
        member.setMemberRole(memberRole);
        return member;
    }

    private void skip(LoanGroupCreateVO vo, Long userId, String realName, String reason) {
        LoanGroupCreateVO.Skipped skipped = new LoanGroupCreateVO.Skipped();
        skipped.setUserId(userId);
        skipped.setRealName(realName);
        skipped.setReason(reason);
        vo.getSkipped().add(skipped);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
