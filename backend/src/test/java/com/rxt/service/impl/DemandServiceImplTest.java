package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.entity.Demand;
import com.rxt.mapper.DemandMapper;
import com.rxt.service.DemandExpireService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M3 求购列表的可见性过滤条件 + 读路径的到期收口防回归。
 *
 * <p><b>为什么需要这个测试</b>：列表的可见性完全由一个 SQL 条件决定：
 * {@code status = 1 AND valid_until >= NOW()}。这条条件有两个失效方式，
 * 而且**都不会报错、不会打日志**：</p>
 * <ul>
 *   <li>被整个删掉 → 已关闭、已过期的需求都会冒出来；</li>
 *   <li>{@code valid_until} 那一半被删掉 → {@code valid_until} 为 NULL 的行会被
 *       {@code NULL >= NOW()} 判为 NULL（而非 TRUE）而静默消失。</li>
 * </ul>
 * <p>真实事故：{@code data.sql} 漏写三列，8 条「进行中」的求购只剩 1 条可见，
 * 排查方式是"比对行数"——因为没有任何检测点。这个测试就是那个缺失的检测点。</p>
 *
 * <p><b>为什么不直接在库里造一行 NULL 来测</b>：修复后 DDL 已把该列收紧为
 * {@code NOT NULL}（见 {@code migrations/2026-09-23_01_demand_not_null.sql}），
 * 库里已经不可能存在 NULL 行，测试也就无从构造。因此这里改为断言
 * **过滤条件本身存在**：只要有人把 {@code valid_until >= ?} 从查询里去掉，
 * 本测试立即变红。</p>
 *
 * <p><b>2026-10-12 补充（W2 到期失效）</b>：新增的 {@code expireOverdue()} 调用
 * 也纳入本类覆盖 —— 它同样属于「不报错、只能靠比对发现」的那一类：
 * 一旦有人把读路径里的这行调用删掉，过期需求的状态就会永远停在 1，
 * 而接口照常返回 200。故补三条断言：列表触发、详情触发、写路径不触发。</p>
 *
 * <p>纯 Mockito，不启动 Spring、不依赖本地 MySQL。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M3 求购列表：可见性过滤条件与到期收口防回归")
class DemandServiceImplTest {

    /**
     * LambdaQueryWrapper 解析字段名依赖 TableInfo 缓存，不初始化会抛
     * 「can not find lambda cache」。这里只需要 Demand。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Demand.class);
    }

    @Mock
    private DemandMapper demandMapper;

    /** W2 新增依赖：读路径的到期收口服务。不 mock 它会注入 null 并让下面用例全红。 */
    @Mock
    private DemandExpireService demandExpireService;

    @InjectMocks
    private DemandServiceImpl demandService;

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<Demand> captureWrapper() {
        when(demandMapper.selectPage(any(), any())).thenReturn(new Page<Demand>());
        demandService.pageDemands(1, 10, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Demand>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(demandMapper).selectPage(any(), captor.capture());

        LambdaQueryWrapper<Demand> wrapper = captor.getValue();
        // 必须主动渲染一次：MP 是在生成 SQL 片段时才把参数值填进
        // paramNameValuePairs 的，不渲染就拿不到任何参数（空 Map）。
        wrapper.getTargetSql();
        return wrapper;
    }

    private static Demand demandOf(Long id, Long publisherId) {
        Demand d = new Demand();
        d.setDemandId(id);
        d.setPublisherId(publisherId);
        return d;
    }

    @Test
    @DisplayName("列表必须同时按 status 与 valid_until 过滤，两个条件缺一不可")
    void pageDemands_filtersByStatusAndValidUntil() {
        String sql = captureWrapper().getTargetSql();

        assertTrue(sql.contains("status"),
                "列表缺少 status 过滤，已关闭的需求会出现在列表里。实际条件：" + sql);
        assertTrue(sql.contains("valid_until"),
                "列表缺少 valid_until 过滤，过期需求会出现在列表里。实际条件：" + sql);
        assertTrue(sql.contains(">="),
                "valid_until 的比较符必须是 >=（NULL >= NOW() 结果为 NULL 而非 TRUE，"
                        + "缺了它会让空值行静默消失）。实际条件：" + sql);
    }

    @Test
    @DisplayName("valid_until 的边界值是「执行查询时的当前时间」，不是写死的日期")
    void pageDemands_usesNowAsBoundary() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        LambdaQueryWrapper<Demand> wrapper = captureWrapper();

        Map<String, Object> params = wrapper.getParamNameValuePairs();
        boolean hasRecentTime = params.values().stream()
                .anyMatch(v -> v instanceof LocalDateTime t && t.isAfter(before));

        assertTrue(hasRecentTime,
                "没有找到以「当前时间」为值的查询参数。若有人把它改成写死的时间，"
                        + "列表的可见性就会随日期推移而失真。实际参数：" + params);
    }

    @Test
    @DisplayName("status 的过滤值是 1（有效）")
    void pageDemands_filtersActiveStatus() {
        Map<String, Object> params = captureWrapper().getParamNameValuePairs();

        assertTrue(params.containsValue(1),
                "status 的过滤值应为 1（有效）。若被改成 0 或其他值，列表语义就反了。"
                        + "实际参数：" + params);
    }

    @Test
    @DisplayName("W2：列表读取前先收口过期状态（否则过期需求永远停在 status=1）")
    void pageDemands_triggersExpireOverdue() {
        when(demandMapper.selectPage(any(), any())).thenReturn(new Page<Demand>());

        demandService.pageDemands(1, 10, null, null);

        verify(demandExpireService).expireOverdue();
    }

    @Test
    @DisplayName("W2：详情读取前同样先收口过期状态")
    void detail_triggersExpireOverdue() {
        when(demandMapper.selectById(1L)).thenReturn(demandOf(1L, 7L));

        demandService.detail(1L);

        verify(demandExpireService).expireOverdue();
    }

    @Test
    @DisplayName("W2 反向对照：写路径（关闭）不触发收口，只做自己那一件事")
    void closeDemand_doesNotTriggerExpire() {
        when(demandMapper.selectById(1L)).thenReturn(demandOf(1L, 7L));

        demandService.closeDemand(7L, 1L);

        verify(demandExpireService, never()).expireOverdue();
        verify(demandMapper).updateById(any(Demand.class));
    }
}
