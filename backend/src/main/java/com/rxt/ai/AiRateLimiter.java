package com.rxt.ai;

import com.rxt.common.BizException;
import com.rxt.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 调用限流：每用户（小时 / 天）+ 全体（天）三层滑动窗口。
 *
 * <p><b>为什么必须有：</b>API Key 是「直接扣钱」的凭据。没有限流的话，一个演示账号
 * 或者一个手滑写成的循环，就能在一个下午把余额烧掉，而且事后只能从账单里看出来。</p>
 *
 * <p><b>为什么用内存而不是 Redis：</b>本项目是单机部署（宝塔 + 单个 JVM），
 * 为一个限流器引入中间件不划算。选择被刻意收敛在本类内部 ——
 * 将来若真的上多实例，把这里换成 Redis 的 {@code INCR + EXPIRE} 即可，
 * 调用方（Service）不需要改一行。</p>
 *
 * <p><b>计数发生在真正调用上游之前：</b>调用失败也会占用一次额度。
 * 这是有意的 —— 否则「上游一直失败」会变成「用户可以无限重试」，
 * 每次重试都真的打到上游（失败请求同样计费），限流就形同虚设。</p>
 *
 * <p><b>清理策略：</b>每个用户的队列只保留最近 24 小时的记录，
 * 每次访问该用户时顺手剔除过期项。队列长度天然被「每天限额」封顶，
 * 因此内存占用是「用户数 × 每日限额」这个量级，不需要额外的清理任务。</p>
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private static final long HOUR_MS = 3600_000L;

    private static final long DAY_MS = 24 * HOUR_MS;

    private static final String MESSAGE_PER_HOUR = "AI 助手调用得有点频繁，请稍后再试，或直接把问题提交给专家";

    private static final String MESSAGE_LIMIT = "AI 助手今天的使用次数已用完，请明天再试，或直接把问题提交给专家";

    private final AiProperties properties;

    /** 每个用户最近的调用时刻（毫秒）。同一个队列同时服务「每小时」和「每天」两个窗口 */
    private final Map<Long, Deque<Long>> userHits = new ConcurrentHashMap<>();

    private final AtomicInteger globalDayCount = new AtomicInteger();

    /** 全局计数所属的日期，用于跨天时把计数归零 */
    private volatile String globalDayKey = LocalDate.now().toString();

    /**
     * 记账并校验。超出任一限额时抛 {@link BizException}，文案是给用户看的具体说明。
     *
     * @param userId 当前登录用户
     */
    public void checkAndRecord(Long userId) {
        long now = System.currentTimeMillis();
        rollGlobalDayIfNeeded();

        if (globalDayCount.get() >= properties.getGlobalPerDay()) {
            throw new BizException(MESSAGE_LIMIT);
        }

        Deque<Long> hits = userHits.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (hits) {
            // 先剔除 24 小时前的记录，两个窗口的判断都基于剩下这些
            while (!hits.isEmpty() && hits.peekFirst() <= now - DAY_MS) {
                hits.pollFirst();
            }
            long inLastHour = hits.stream().filter(at -> at > now - HOUR_MS).count();
            if (inLastHour >= properties.getPerUserPerHour()) {
                throw new BizException(MESSAGE_PER_HOUR);
            }
            if (hits.size() >= properties.getPerUserPerDay()) {
                throw new BizException(MESSAGE_LIMIT);
            }
            hits.addLast(now);
        }

        globalDayCount.incrementAndGet();
    }

    private void rollGlobalDayIfNeeded() {
        String today = LocalDate.now().toString();
        if (today.equals(globalDayKey)) {
            return;
        }
        synchronized (this) {
            if (!today.equals(globalDayKey)) {
                globalDayKey = today;
                globalDayCount.set(0);
            }
        }
    }
}
