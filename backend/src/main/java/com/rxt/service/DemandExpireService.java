package com.rxt.service;

import com.rxt.mapper.DemandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 求购需求「到期即失效」的<b>唯一收口点</b>（status 1 → 0）。
 *
 * <p>需求依据：SRS「需求超过有效期后系统自动置为失效」。实现方式选择见
 * {@link #expireOverdue()} 的说明。</p>
 *
 * <p><b>为什么不把这条 UPDATE 直接写进 {@link DemandService} 的实现里</b>：
 * 这是全模块唯一一处「不由用户动作触发、而由时间流逝触发」的状态变更。
 * 独立成类之后，它的触发方式可以随时替换（当前是读路径惰性调用；
 * 将来若要接调度器，只需在这里补一个入口，业务方法的实现一行都不用动），
 * 也不会出现「两处各写一份 SQL、口径慢慢漂开」的情况。
 * 它不被 Controller 直接暴露、没有对外契约，故按项目习惯不拆接口，只提供一个类。</p>
 */
@Service
@RequiredArgsConstructor
public class DemandExpireService {

    private final DemandMapper demandMapper;

    /**
     * 把「已过期（{@code valid_until < NOW()}）但仍停留在有效状态」的求购置为已关闭。
     *
     * <p><b>为什么用惰性收口，而不是加一个 {@code @Scheduled} 常驻定时任务</b>：
     * 《详细设计说明书》§10.2.5 对本模块有明文设计立场 ——
     * 「在明确需要之前不引入调度器……这里真正缺的只是『到期后把 status 置为 0』
     * 这一步状态变更；在该状态变更明确需要之前，不为此引入常驻调度组件」。
     * 也就是说这里缺的是<b>状态变更本身</b>，而不是「必须由调度器完成的状态变更」。
     * 在读取路径上收口，既补上了这一步，又不与上述立场冲突。</p>
     *
     * <p><b>惰性是否满足「自动失效」的验收口径</b>：所有会展示 {@code status} 的出口
     * （列表、详情，以及将来「我的发布」的读取）都汇入这里，因此对调用方而言，
     * 拿到的状态永远已经校正过。<b>已知边界</b>：若长期无人访问，库里的
     * {@code status} 会一直停在 1 —— 这是惰性方案的固有特性，也是它的优点
     * （无请求时不做无用功）。若要求「无人访问时库里也必须是 0」，那就必须引入
     * 调度器，属与 §10.2.5 立场冲突的决策，需组长确认后再改，改动量仅为在本类
     * 之上加一个触发入口。</p>
     *
     * <p><b>为什么带 {@code @Transactional}</b>：与项目其他写操作口径一致。
     * 单条 UPDATE 本身已是原子的，这里的事务只为「将来扩展成多语句」预留一致性边界。</p>
     *
     * @return 本次置为失效的行数；0 = 当前没有到期需求（绝大多数请求的常态）
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdue() {
        return demandMapper.expireOverdue();
    }
}
