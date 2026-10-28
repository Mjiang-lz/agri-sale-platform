package com.rxt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>分页插件依赖 mybatis-plus-jsqlparser 模块（3.5.9 起 JSqlParser 被拆为独立模块，
 * 未引入该依赖时 PaginationInnerInterceptor 无法加载）。</p>
 *
 * <p><b>注意这里只拦上界，不拦下界。</b>分页插件把 {@code size < 0} 当作「不分页」处理 ——
 * 既不跑 count，又把整表数据一次性返回；{@code maxLimit} 对这条支路无效
 * （实测 {@code ?pageSize=-5} 会返回全量 24 条且 {@code total=0}）。
 * 下界由 {@code common/PageParamInterceptor} 在请求入口统一拦下。</p>
 *
 * <p>之所以不在这一层拦下界：MyBatis 的 {@code BaseExecutor} 会把查询链路上抛出的任何异常
 * 包成 {@code PersistenceException}，自定义的 {@code BizException} 会被包在里面，
 * 到了全局异常处理器已经认不出来，最终以 500「系统繁忙」返回 —— 实测踩到过。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /** 分页单页条数上限，防止客户端一次拉全表 */
    private static final long MAX_PAGE_SIZE = 200L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
