package com.rxt.vo;

/**
 * AI 助手的可用状态，供前端决定「入口显不显示」。
 *
 * <p><b>为什么要一个独立接口：</b>没有它，前端只能靠「点了之后看报错」来判断
 * AI 有没有开 —— 于是未配置密钥的环境里会出现一个点了报错的按钮。
 * 而「看起来能点却点不动」正是本项目刚修过的一类缺陷（落地页资讯条目），
 * 宁可没有入口，也不要一个假的入口。</p>
 *
 * @param enabled 是否可用（开关打开且密钥非空）
 * @param model   模型名；未启用时为 null（会被全局的 non_null 策略直接省略掉）
 */
public record AiStatusVO(boolean enabled, String model) {
}
