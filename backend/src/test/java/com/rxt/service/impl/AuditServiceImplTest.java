package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.AuditActionDTO;
import com.rxt.entity.AuditRecord;
import com.rxt.entity.Comment;
import com.rxt.entity.Knowledge;
import com.rxt.entity.Product;
import com.rxt.entity.User;
import com.rxt.mapper.AuditRecordMapper;
import com.rxt.mapper.CommentMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.vo.AuditDailyStatVO;
import com.rxt.vo.AuditStatisticsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内容审核「审核动作」单元测试（M6 / Story 71127833 的 T3「补充审核状态流转的单测」）。
 *
 * <p><b>为什么不起 Spring 容器</b>：本类只关心 {@code auditContent} 的分支与副作用，
 * 用纯 Mockito 就够了。好处有三：① 不需要本地 MySQL，谁 clone 下来都能跑；
 * ② 完全避开 Spring Boot 4 测试注解的变动（{@code @MockBean} 在 4.x 已移除，
 * 替代品是 {@code @MockitoBean}）；③ 跑得快。</p>
 *
 * <p><b>为什么不用 {@code @SpringBootTest}</b>：仓库里现存的
 * {@code RxtApplicationTests.contextLoads()} 需要连本地库，属于「别人机器上跑不过」的测试，
 * 不适合作为业务单测的范式。</p>
 *
 * <p><b>本类测不到的</b>：{@code @Transactional} 的回滚。注解要 Spring 容器才生效，
 * 而本类不起容器 —— 回滚由 Spring 保证，属于集成测试的范围，不在这里假装测过。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M6 内容审核 · 审核动作 auditContent")
class AuditServiceImplTest {

    /**
     * 让 MyBatis-Plus 认识这几个实体（纯 Mockito 场景下的必要补偿）。
     *
     * <p>被测代码用的是 {@code LambdaUpdateWrapper.set(Product::getAuditStatus, ...)} 这种
     * lambda 写法，MP 需要一份「lambda → 数据库列名」的映射；这份映射由实体元数据
     * （{@code TableInfo}）提供，而 {@code TableInfo} 是 MyBatis 启动时扫描实体建立的
     * —— <b>纯 Mockito 不起容器，这份缓存就是空的</b>，于是报
     * {@code can not find lambda cache for this entity [com.rxt.entity.Product]}。</p>
     *
     * <p>手动补一次初始化即可。代价是 4 行代码，换来「不需要 MySQL、启动只要几百毫秒」。
     * 这是一个真实的取舍点：<b>只要被测 Service 里出现了 LambdaWrapper，就需要这段</b>，
     * 后续给别的模块写单测可以直接复制。</p>
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(Product.class, Knowledge.class, Comment.class,
                AuditRecord.class, User.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Mock
    private ProductMapper productMapper;
    @Mock
    private KnowledgeMapper knowledgeMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private AuditRecordMapper auditRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PermissionChecker permissionChecker;

    @InjectMocks
    private AuditServiceImpl auditService;

    /** 演示账号里的管理员 user_id */
    private static final Long AUDITOR_ID = 1L;

    /** 业务表状态词表 */
    private static final String STATUS_PENDING = "待审核";
    private static final String STATUS_PASSED = "已通过";

    /** t_audit 结论词表 */
    private static final String RESULT_PASS = "通过";
    private static final String RESULT_REJECT = "不通过";

    // =====================================================================
    // 一、入参校验：这些分支必须「在碰数据库之前」就失败
    // =====================================================================

    @Test
    @DisplayName("审核结论不是「通过/不通过」→ 400，且一条 SQL 都不发")
    void rejectUnknownAuditResult() {
        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action("maybe", null)));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("通过"));
        // 结论非法时立刻返回，不该有任何查询/写入 —— 这是「先校验后动手」的证据
        verifyNoInteractions(productMapper, knowledgeMapper, commentMapper, auditRecordMapper);
    }

    @Test
    @DisplayName("驳回但意见是空白 → 400（发布者要照着意见改，没意见等于没驳回）")
    void rejectBlankRemarkOnRejection() {
        // 注意是「三个空格」不是空串：必须用 hasText 判，用 != null 会漏掉这种情况
        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action(RESULT_REJECT, "   ")));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("审核意见"));
    }

    @Test
    @DisplayName("非管理员调用 → 权限校验拒绝（403），业务逻辑完全不执行")
    void rejectNonAdmin() {
        doThrow(new BizException(ResultCode.FORBIDDEN)).when(permissionChecker).requireAdmin();

        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action(RESULT_PASS, null)));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(productMapper, auditRecordMapper);
    }

    @Test
    @DisplayName("内容类型不在白名单（如 EXPERT 主体审核）→ 400")
    void rejectUnknownTargetType() {
        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("EXPERT", 1L, AUDITOR_ID, action(RESULT_PASS, null)));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("内容类型"));
    }

    // =====================================================================
    // 二、状态流转：内容不存在 / 已被审过 / 并发被抢先
    // =====================================================================

    @Test
    @DisplayName("商品不存在 → 404")
    void productNotFound() {
        when(productMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 99L, AUDITOR_ID, action(RESULT_PASS, null)));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("商品已被审过（状态非「待审核」）→ 400，且不写审核流水")
    void productAlreadyAudited() {
        Product audited = pendingProduct(1L);
        audited.setAuditStatus(STATUS_PASSED);
        when(productMapper.selectById(1L)).thenReturn(audited);

        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action(RESULT_PASS, null)));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不能重复审核"));
        // 关键：被拒的一侧绝不能留下流水，否则审核记录会多出一条假的
        verify(auditRecordMapper, never()).insert(any(AuditRecord.class));
    }

    @Test
    @DisplayName("并发：两人同时点开同一条，后提交者受影响 0 行 → 400 提示刷新，不写流水")
    void productConcurrentUpdateConflict() {
        when(productMapper.selectById(1L)).thenReturn(pendingProduct(1L));
        // 查询时还是「待审核」，但 UPDATE 的 WHERE 里也带了这个条件 → 说明已被别人改掉，影响 0 行
        when(productMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action(RESULT_PASS, null)));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("请刷新"));
        verify(auditRecordMapper, never()).insert(any(AuditRecord.class));
    }

    // =====================================================================
    // 三、顺利通过：副作用（联动上架、落流水）才是要断言的重点
    // =====================================================================

    @Test
    @DisplayName("商品审核通过 → 联动上架 + 落流水（审核人/时间/结论齐全）")
    void productPassedShouldShelfAndRecord() {
        when(productMapper.selectById(1L)).thenReturn(pendingProduct(1L));
        when(productMapper.update(any(), any())).thenReturn(1);

        auditService.auditContent("PRODUCT", 1L, AUDITOR_ID, action(RESULT_PASS, "资质齐全"));

        // 1) 商品被更新（审核状态 + 上下架联动，只改状态的话商品「审核通过却买不到」）
        verify(productMapper).update(any(), any());

        // 2) 流水被写入，且字段完整 —— 捕获真实传入的对象来断言，不靠猜
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(captor.capture());
        AuditRecord saved = captor.getValue();

        assertEquals("PRODUCT", saved.getTargetType());
        assertEquals(1L, saved.getTargetId());
        assertEquals(RESULT_PASS, saved.getAuditResult());
        assertEquals("资质齐全", saved.getAuditRemark());
        assertEquals(AUDITOR_ID, saved.getAuditorId());
        assertNotNull(saved.getAuditTime(), "审核时间必须落库，否则流水看不出是什么时候审的");
    }

    @Test
    @DisplayName("知识审核通过 → 只改审核状态（t_knowledge 没有上下架字段，不能去碰商品表）")
    void knowledgePassedShouldOnlyChangeStatus() {
        Knowledge knowledge = new Knowledge();
        knowledge.setKnowledgeId(2L);
        knowledge.setAuditStatus(STATUS_PENDING);
        knowledge.setTitle("柑橘黄龙病防治");
        knowledge.setAuthorId(9L);
        knowledge.setCreateTime(LocalDateTime.now());
        when(knowledgeMapper.selectById(2L)).thenReturn(knowledge);
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        auditService.auditContent("KNOWLEDGE", 2L, AUDITOR_ID, action(RESULT_PASS, null));

        verify(knowledgeMapper).update(any(), any());
        // 知识审核不该顺手去动商品表
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("评论驳回 → 只改审核状态，不做物理删除，意见落进流水")
    void commentRejectedShouldOnlyChangeStatus() {
        Comment comment = new Comment();
        comment.setCommentId(3L);
        comment.setAuditStatus(STATUS_PENDING);
        comment.setContent("加我微信买橙子");
        comment.setUserId(10L);
        comment.setCreateTime(LocalDateTime.now());
        when(commentMapper.selectById(3L)).thenReturn(comment);
        when(commentMapper.update(any(), any())).thenReturn(1);

        auditService.auditContent("COMMENT", 3L, AUDITOR_ID, action(RESULT_REJECT, "含联系方式，属广告"));

        verify(commentMapper).update(any(), any());
        // 驳回的评论要留痕可回溯，不能删掉
        verify(commentMapper, never()).deleteById(any());

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(captor.capture());
        assertEquals(RESULT_REJECT, captor.getValue().getAuditResult());
        assertEquals("COMMENT", captor.getValue().getTargetType());
    }

    // =====================================================================
    // 测试数据构造
    // =====================================================================

    private AuditActionDTO action(String result, String remark) {
        AuditActionDTO dto = new AuditActionDTO();
        dto.setAuditResult(result);
        dto.setAuditRemark(remark);
        return dto;
    }

    private Product pendingProduct(Long productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductName("赣南脐橙");
        product.setSellerId(8L);
        product.setAuditStatus(STATUS_PENDING);
        product.setCreateTime(LocalDateTime.now());
        return product;
    }

    // =====================================================================
    // 四、审核工作量统计 statistics
    // 口径见 docs/接口约定-M6内容审核.md 第八节。本组用例守三件事：
    //   ① 类型范围不能漏 —— 漏了就把 M1 的 USER / EXPERT 主体审核算成内容审核量；
    //   ② 趋势固定 7 个点、含今天、无数据的日期补 0（不能缺项，也不能是 null）；
    //   ③ 时间窗口是半开区间、且列上不套函数（套了索引就失效）。
    // =====================================================================

    @Test
    @DisplayName("统计：非管理员 → 403，且一条 SQL 都不发")
    void statisticsRejectNonAdmin() {
        doThrow(new BizException(ResultCode.FORBIDDEN)).when(permissionChecker).requireAdmin();

        BizException ex = assertThrows(BizException.class,
                () -> auditService.statistics(AUDITOR_ID, null));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(auditRecordMapper);
    }

    @Test
    @DisplayName("统计：targetType 传 USER / EXPERT（主体审核）→ 400，不能悄悄算进来")
    void statisticsRejectSubjectAuditType() {
        for (String type : List.of("USER", "EXPERT")) {
            BizException ex = assertThrows(BizException.class,
                    () -> auditService.statistics(AUDITOR_ID, type));
            assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("内容类型不合法"));
        }
        // 校验必须在查询之前，否则非法类型会先发出一条范围错的 SQL 才报错
        verifyNoInteractions(auditRecordMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("统计：不传类型 → 三类内容合计（不是「不过滤」，主体审核不能被算进来）")
    void statisticsWithoutTypeCoversThreeContentTypes() {
        when(auditRecordMapper.countByAuditor(any(), any())).thenReturn(12L);
        when(auditRecordMapper.selectDailyStats(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        AuditStatisticsVO vo = auditService.statistics(AUDITOR_ID, null);

        assertEquals(12L, vo.getMyReviewedTotal(), "累计已审数应原样透传自流水表统计");

        ArgumentCaptor<List<String>> forTotal = ArgumentCaptor.forClass(List.class);
        verify(auditRecordMapper).countByAuditor(eq(AUDITOR_ID), forTotal.capture());
        assertEquals(List.of("PRODUCT", "KNOWLEDGE", "COMMENT"), forTotal.getValue());

        ArgumentCaptor<List<String>> forTrend = ArgumentCaptor.forClass(List.class);
        verify(auditRecordMapper).selectDailyStats(any(), any(), forTrend.capture(), any(), any());
        assertEquals(List.of("PRODUCT", "KNOWLEDGE", "COMMENT"), forTrend.getValue(),
                "趋势查询同样必须限定在三种内容类型内");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("统计：传了 targetType → 两个查询都只统计该类型")
    void statisticsWithTypeFiltersSingleType() {
        when(auditRecordMapper.countByAuditor(any(), any())).thenReturn(3L);
        when(auditRecordMapper.selectDailyStats(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        auditService.statistics(AUDITOR_ID, "KNOWLEDGE");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditRecordMapper).countByAuditor(eq(AUDITOR_ID), captor.capture());
        assertEquals(List.of("KNOWLEDGE"), captor.getValue());
    }

    @Test
    @DisplayName("统计：趋势固定 7 个点、含今天、升序连续，无数据的日期补 0")
    void statisticsFillsMissingDaysWithZero() {
        LocalDate today = LocalDate.now();
        LocalDate twoDaysAgo = today.minusDays(2);

        when(auditRecordMapper.countByAuditor(any(), any())).thenReturn(0L);
        // 数据库只返回「有记录」的日期（这里是 2 天前那一条），其余 6 天要由 Service 补 0
        when(auditRecordMapper.selectDailyStats(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new AuditDailyStatVO(twoDaysAgo.toString(), 3L, 1L)));

        List<AuditDailyStatVO> trend = auditService.statistics(AUDITOR_ID, null).getLast7Days();

        assertEquals(7, trend.size(), "必须固定 7 个点，前端按固定长度画折线");
        for (int i = 0; i < trend.size(); i++) {
            // 升序、连续、且以「今天 - 6 天」开头、以「今天」结尾
            assertEquals(today.minusDays(6L - i).toString(), trend.get(i).getDate());
        }

        AuditDailyStatVO hit = trend.get(4);   // today - 2
        assertEquals(twoDaysAgo.toString(), hit.getDate());
        assertEquals(3L, hit.getReviewed());
        assertEquals(1L, hit.getRejected());

        AuditDailyStatVO zero = trend.get(6);  // today
        assertEquals(today.toString(), zero.getDate(), "最后一个点必须是今天");
        assertEquals(0L, zero.getReviewed(), "今天还没审过就是 0 —— 前端不该看到 null 或缺项");
        assertEquals(0L, zero.getRejected());
    }

    @Test
    @DisplayName("统计：时间窗口为半开区间 [今天-6 00:00, 明天 00:00)，结果词表由 Java 常量传入")
    void statisticsUsesHalfOpenWindowAndJavaWordList() {
        when(auditRecordMapper.countByAuditor(any(), any())).thenReturn(0L);
        when(auditRecordMapper.selectDailyStats(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        auditService.statistics(AUDITOR_ID, null);

        LocalDate today = LocalDate.now();
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> passCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rejectCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditRecordMapper).selectDailyStats(startCaptor.capture(), endCaptor.capture(),
                any(), passCaptor.capture(), rejectCaptor.capture());

        assertEquals(today.minusDays(6).atStartOfDay(), startCaptor.getValue(),
                "窗口起点是「今天 - 6 天」的 00:00");
        assertEquals(today.plusDays(1).atStartOfDay(), endCaptor.getValue(),
                "上界用「明天 00:00」半开区间，写「今天 23:59:59」会漏掉当天最后一秒的数据");
        // 结论词表（通过 / 不通过）在 Java 侧已有唯一来源，SQL 里不再写一份中文常量，
        // 否则改词表时漏改 SQL 一处就会静默统计出错
        assertEquals(RESULT_PASS, passCaptor.getValue());
        assertEquals(RESULT_REJECT, rejectCaptor.getValue());
    }
}
