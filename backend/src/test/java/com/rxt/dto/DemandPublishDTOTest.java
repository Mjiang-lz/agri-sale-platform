package com.rxt.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布 / 编辑求购入参的有效期范围校验。
 *
 * <p><b>为什么这两条校验必须存在</b>（需求 §2.7.3 UC-TRD-02）：</p>
 * <ul>
 *   <li>「有效期不超过 90 天」是需求明文约束；</li>
 *   <li>「必须晚于当前时间」是逻辑必然 —— 填过去的日期能发布成功，但该需求立刻被
 *       列表的 {@code valid_until >= NOW()} 过滤掉，用户看到"发布成功"却在列表里
 *       找不到，会当成发布失败（契约文档 §1.2 推论 2 记录过这个现象）。</li>
 * </ul>
 *
 * <p><b>为什么用 Hibernate Validator 而不是 Spring 的 MockMvc</b>：这两条约束是
 * DTO 上的声明式校验，脱离 Web 层直接 validate 更聚焦，也不受 Spring Boot 4
 * 测试注解变动影响。校验结果就是 {@code GlobalExceptionHandler} 转成 {@code 400}
 * 文案的来源，因此断言消息文案等价于断言接口响应。</p>
 */
@DisplayName("M3 发布求购入参：有效期范围校验")
class DemandPublishDTOTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private static final String NOT_FUTURE = "需求有效期必须晚于当前时间";
    private static final String TOO_FAR = "需求有效期不能超过 90 天";
    private static final String REQUIRED = "请填写需求有效期";

    private static DemandPublishDTO valid() {
        DemandPublishDTO dto = new DemandPublishDTO();
        dto.setCategory("水果");
        dto.setQuantity(100);
        dto.setUnit("斤");
        dto.setExpectPrice(new BigDecimal("3.50"));
        dto.setDescription("校验用夹具，不会落库");
        dto.setRegion("浙江杭州");
        dto.setValidUntil(LocalDateTime.now().plusDays(30));
        dto.setContact("13800138000");
        return dto;
    }

    private static Set<String> messages(DemandPublishDTO dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("合法入参（30 天后）应零违规")
    void validDto_hasNoViolation() {
        Set<String> msgs = messages(valid());
        assertTrue(msgs.isEmpty(), "合法入参不应有违规，实际：" + msgs);
    }

    @Test
    @DisplayName("有效期是过去的日期 → 「" + NOT_FUTURE + "」")
    void pastValidUntil_isRejected() {
        DemandPublishDTO dto = valid();
        dto.setValidUntil(LocalDateTime.now().minusDays(1));

        assertTrue(messages(dto).contains(NOT_FUTURE),
                "过去的日期必须被拦住，否则会「发布成功却立刻不可见」。实际：" + messages(dto));
    }

    @Test
    @DisplayName("有效期超过 90 天 → 「" + TOO_FAR + "」")
    void beyond90Days_isRejected() {
        DemandPublishDTO dto = valid();
        dto.setValidUntil(LocalDateTime.now().plusDays(91));

        assertTrue(messages(dto).contains(TOO_FAR),
                "超 90 天必须被拦住（需求 §2.7.3 约束条件）。实际：" + messages(dto));
    }

    @Test
    @DisplayName("边界：正好 90 天应放行（上限是 90 而不是 89）")
    void exactly90Days_isAccepted() {
        DemandPublishDTO dto = valid();
        dto.setValidUntil(LocalDateTime.now().plusDays(90));

        assertFalse(messages(dto).contains(TOO_FAR),
                "「不超过 90 天」意味着 90 天本身是合法的。实际：" + messages(dto));
    }

    @Test
    @DisplayName("有效期为 null 时只报「" + REQUIRED + "」，不叠加另外两条（避免矛盾提示）")
    void nullValidUntil_reportsOnlyRequired() {
        DemandPublishDTO dto = valid();
        dto.setValidUntil(null);

        Set<String> msgs = messages(dto);
        assertTrue(msgs.contains(REQUIRED), "应提示必填。实际：" + msgs);
        assertFalse(msgs.contains(NOT_FUTURE),
                "字段为空时不该同时抱怨「必须晚于当前时间」——同一字段一次只报一条。实际：" + msgs);
        assertFalse(msgs.contains(TOO_FAR),
                "字段为空时不该同时抱怨「不能超过 90 天」——同一字段一次只报一条。实际：" + msgs);
    }
}
