package com.rxt.service;

import com.rxt.mapper.DemandMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M3 求购「到期即失效」（W2）的语义防回归。
 *
 * <p>这条状态变更由一行原生 UPDATE 的 <b>WHERE 条件</b>决定成败，而它出错的四种方式
 * 都不会报错、也不会打日志 —— 只能靠断言钉住：</p>
 * <ol>
 *   <li>{@code status = 1} 被去掉 → 每次请求都白写一遍已关闭的行（无害但纯浪费）；</li>
 *   <li>{@code valid_until < NOW()} 的 {@code <} 被写成 {@code <=} →
 *       与列表可见条件 {@code valid_until >= NOW()} 出现重叠：一条需求在到期的那一瞬间
 *       既被列表判为不可见、又被判为"尚未到期"，留下中间态；</li>
 *   <li>把 {@code NOW()} 换成写死的时间 → 判定随日期推移失真（与列表侧同一个坑）；</li>
 *   <li>丢掉 {@code update_time = NOW()} → 自动填充在注解 SQL 上不生效，
 *       留下过期的 update_time（数据看着对、字段是旧的）。</li>
 * </ol>
 *
 * <p>断言全部落在**语义片段**上而非整段文本相等，这样格式化或加注释不会误报，
 * 而上面四种改动一定会红。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M3 求购到期失效：状态变更语义防回归")
class DemandExpireServiceTest {

    @Mock
    private DemandMapper demandMapper;

    @InjectMocks
    private DemandExpireService demandExpireService;

    @Test
    @DisplayName("expireOverdue 只委托给 Mapper 上那一条 UPDATE（不在服务层再写一套判断）")
    void expireOverdue_delegatesToMapper() {
        when(demandMapper.expireOverdue()).thenReturn(3);

        assertEquals(3, demandExpireService.expireOverdue(),
                "返回值应为实际置为失效的行数，供调用方/将来接调度器时观察效果");
        verify(demandMapper).expireOverdue();
    }

    @Test
    @DisplayName("到期判定 SQL：只动 status=1、条件是 valid_until < NOW()、同时刷新 update_time")
    void expireOverdue_sqlKeepsItsSemantics() {
        String sql = readExpireSql();

        assertTrue(sql.contains("status = 0"),
                "必须把 status 置为 0（已关闭）。实际 SQL：" + sql);
        assertTrue(sql.contains("status = 1"),
                "必须限定 status = 1，否则已关闭的行会被反复重写。实际 SQL：" + sql);
        assertTrue(sql.contains("valid_until < NOW()"),
                "到期条件必须是 valid_until < NOW()（严格小于）。实际 SQL：" + sql);
        assertFalse(sql.replaceAll("\\s+", " ").contains("valid_until <= NOW()"),
                "valid_until <= NOW() 会与列表的 valid_until >= NOW() 重叠，"
                        + "在到期瞬间留下「既不可见又未失效」的中间态。实际 SQL：" + sql);
        assertTrue(sql.contains("update_time"),
                "注解里的原生 SQL 走不到 @TableField 的自动填充，必须显式写 update_time。"
                        + "实际 SQL：" + sql);
    }

    /**
     * 直接读 {@code @Update} 注解里的 SQL 文本。
     *
     * <p>之所以允许测"实现细节"：这条 SQL 的 WHERE 条件就是本功能的全部业务规则，
     * 且它没有别的可观察出口（不做真实写库就无法验证）。与
     * {@code DemandServiceImplTest} 断言 wrapper 的 SQL 片段是同一套做法。
     * 若将来改写成 XML mapper，这个测试会红 —— 届时把断言迁到 XML 上即可，
     * 不该直接删掉。</p>
     */
    private static String readExpireSql() {
        Update update;
        try {
            update = DemandMapper.class.getMethod("expireOverdue").getAnnotation(Update.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("DemandMapper#expireOverdue 不存在，到期失效已失去实现", e);
        }
        assertNotNull(update, "expireOverdue 必须保留 @Update 注解（改 XML 时请同步本测试）");
        String[] value = update.value();
        assertTrue(value.length > 0, "@Update 的 SQL 不能为空");
        return value[0].replaceAll("\\s+", " ");
    }
}
