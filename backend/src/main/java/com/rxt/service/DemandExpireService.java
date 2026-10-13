package com.rxt.service;

/**
 * 求购需求「到期即失效」的<b>唯一收口点</b>（status 1 → 0）。
 *
 * <p>需求依据：SRS「需求超过有效期后系统自动置为失效」。任何会展示 {@code status} 的出口
 * （列表、详情，以及将来「我的发布」的读取）都应在读取前调用 {@link #expireOverdue()}，
 * 这样对调用方而言，拿到的状态永远是已经校正过的。</p>
 *
 * <p><b>为什么为它留一个接口</b>：它不被 Controller 直接暴露、没有对外 HTTP 契约，
 * 接口的存在只为与项目「{@code service} 放契约、{@code service.impl} 放实现」的分层
 * 保持一致（它原是直接放在 {@code service} 包下的具体类，与同目录 21 个接口不齐）。
 * 实现方式的取舍（惰性收口，而非 {@code @Scheduled} 常驻调度）见实现类的说明。</p>
 */
public interface DemandExpireService {

    /**
     * 把「已过期（{@code valid_until < NOW()}）但仍停留在有效状态」的求购置为已关闭。
     *
     * @return 本次置为失效的行数；0 = 当前没有到期需求（绝大多数请求的常态）
     */
    int expireOverdue();
}
