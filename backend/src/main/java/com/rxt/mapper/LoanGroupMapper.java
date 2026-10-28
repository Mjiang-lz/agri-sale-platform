package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.LoanGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 联合贷款小组数据访问接口（t_loan_group）。
 *
 * <p><b>成员关系为什么不查这张表：</b>按 {@code data.sql} 第 15 节确定的设计，
 * 小组成员由成员自己的 {@code t_finance_apply.group_id} 反向表达，<b>不单独建成员表</b>。
 * 所以「组里有谁」「谁已经在别的组」这两类查询必须走 t_finance_apply，
 * 本接口把它收在一处，避免各 Service 各写一遍 JOIN。</p>
 */
@Mapper
public interface LoanGroupMapper extends BaseMapper<LoanGroup> {

    /**
     * 按主键取小组并<b>加行锁</b>。
     *
     * <p>加入小组是「先数人数、再插记录」的复合动作，两步之间存在竞态：
     * 并发进来两个请求，都读到「现有 2 人、上限 3 人」，于是都判定可以加入，
     * 结果落成 4 人 —— 也就是团队红线里写的「并发超员」。
     * 单靠 {@code @Transactional} 挡不住：MySQL 默认隔离级别是 REPEATABLE READ，
     * 事务不会给「另一事务也在读的普通行」加锁，两个事务都能读到同一快照。</p>
     *
     * <p>{@code FOR UPDATE} 给这一行加排他锁，后来者必须等前一个事务提交，
     * 于是读到的是已更新的人数。因此本方法<b>只能在事务内调用</b>
     * （自动提交模式下锁会立刻释放，等于没加）。</p>
     */
    @Select("SELECT * FROM `t_loan_group` WHERE `group_id` = #{groupId} FOR UPDATE")
    LoanGroup selectByIdForUpdate(@Param("groupId") Long groupId);

    /**
     * 统计某农户当前「进行中」小组的个数，用于「同时最多参与一个进行中的小组」这条规则。
     *
     * <p><b>「进行中」只算 {@code 组建中} 与 {@code 已提交}：</b>这两个状态成员名单还能变动，
     * 是这条规则真正要防的场景。{@code 已受理} 之后小组已送到银行、成员名单冻结，
     * 此时把农户算作「参与中」会让他<b>永远无法再组建新小组</b> —— 而种子数据里
     * 三位农户恰好都被一个「已受理」的历史小组占住，规则一旦放宽范围就会把演示锁死。</p>
     *
     * <p>过滤条件全部下推到 SQL：JOIN 之后只回一个计数，不把候选行捞到应用层再筛。</p>
     */
    @Select("SELECT COUNT(*) FROM `t_finance_apply` a "
            + "INNER JOIN `t_loan_group` g ON a.`group_id` = g.`group_id` "
            + "WHERE a.`farmer_id` = #{farmerId} "
            + "AND g.`group_status` IN ('组建中', '已提交')")
    int countActiveMembership(@Param("farmerId") Long farmerId);

    /**
     * 取所有正处在「进行中」小组里的农户主键（去重）。
     *
     * <p>智能推荐要「过滤已组队关系」（REQ-07）：已经组好队的农户不应再被推给别人。
     * 一次查全量、在内存里做集合判断，好过对每个候选人各查一次库（N+1）。</p>
     */
    @Select("SELECT DISTINCT a.`farmer_id` FROM `t_finance_apply` a "
            + "INNER JOIN `t_loan_group` g ON a.`group_id` = g.`group_id` "
            + "WHERE g.`group_status` IN ('组建中', '已提交')")
    List<Long> selectActiveMemberFarmerIds();
}
