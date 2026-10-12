package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Demand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 求购需求数据访问接口（t_demand）。
 */
@Mapper
public interface DemandMapper extends BaseMapper<Demand> {

    /**
     * 把「已过期但仍标记为有效」的求购批量置为已关闭（status 1 → 0）。
     *
     * <p><b>为什么比较符是 {@code <} 而不是 {@code <=}</b>：列表的可见条件是
     * {@code status = 1 AND valid_until >= NOW()}，这里的失效条件取它的<b>补集</b>
     * （{@code status = 1 AND valid_until < NOW()}），两者恰好不重不漏 ——
     * status = 1 的行被「此刻仍可见」与「此刻应置失效」分完，不会出现某一行
     * 既不在列表里、又没被置 0 的中间态。</p>
     *
     * <p><b>为什么是一条 UPDATE 而不是「先查出来再逐条 updateById」</b>：与
     * {@link ProductMapper#deductStock} 同理 —— 判断条件写进 WHERE 后由数据库在
     * 同一条语句内完成，并发触发时第二次的 WHERE 已不成立、影响 0 行，天然幂等，
     * 不需要额外加锁或去重。</p>
     *
     * <p>SQL 里显式写 {@code update_time = NOW()}：{@code @TableField(fill = INSERT_UPDATE)}
     * 的自动填充只在 MyBatis-Plus 的 insert / updateById 链路上生效，注解里的原生 SQL
     * 走不到那段逻辑，不写会留下过期的 update_time。</p>
     *
     * @return 本次实际置为失效的行数：0 = 当前没有到期需求（绝大多数请求的常态）
     */
    @Update("UPDATE t_demand SET status = 0, update_time = NOW() "
            + "WHERE status = 1 AND valid_until < NOW()")
    int expireOverdue();
}
