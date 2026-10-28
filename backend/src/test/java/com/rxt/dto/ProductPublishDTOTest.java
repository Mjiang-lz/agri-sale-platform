package com.rxt.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布 / 编辑商品入参的上限校验：每个字段的上限都要与 {@code t_product} 的列宽对齐。
 *
 * <p><b>为什么这些断言值得单独守护</b>：这类缺陷的共同特征是「不报错、不打日志，
 * 只是安静地把一个 400 说成 500」。注解比列宽宽松时，越界值会一路穿过 Controller、
 * Service，到 JDBC 才被数据库拒绝（本机 {@code sql_mode} 含 {@code STRICT_TRANS_TABLES}，
 * 实测 {@code price = 99999999999.99} 报 {@code DataError 1264 Out of range}），
 * 最终被全局兜底分支回成 {@code 500 系统繁忙} —— 前端只能提示「请稍后重试」，
 * 而调用方其实少打一位数就能过。改列宽时若忘了同步这里，就会退化成这个状态。</p>
 *
 * <p>用 Hibernate Validator 直接 validate DTO，不启动 Spring、不连库：
 * 校验结果就是 {@code GlobalExceptionHandler} 转成 400 文案的来源，
 * 因此断言消息文案等价于断言接口响应。</p>
 */
@DisplayName("M2 发布商品入参：字段上限与 t_product 列宽对齐")
class ProductPublishDTOTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private static final String PRICE_DIGITS = "单价最多 10 位整数、2 位小数";
    private static final String PRICE_POSITIVE = "单价必须大于 0";
    private static final String NAME_TOO_LONG = "商品名称不能超过 100 字";

    /** 一份各字段都合法的入参（校验用夹具，不会落库） */
    private static ProductPublishDTO valid() {
        ProductPublishDTO dto = new ProductPublishDTO();
        dto.setProductName("赣南脐橙 5 斤装");
        dto.setCategory("水果");
        dto.setPrice(new BigDecimal("29.90"));
        dto.setStock(100);
        dto.setUnit("斤");
        dto.setDescription("当季现摘，甜度 13 以上");
        dto.setOrigin("江西赣州");
        dto.setImageUrl("/products/p01.jpg");
        dto.setImages(List.of("/products/p01.jpg"));
        return dto;
    }

    private static Set<String> messages(ProductPublishDTO dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static String repeat(char ch, int times) {
        return new String(new char[times]).replace('\0', ch);
    }

    @Test
    @DisplayName("合法入参应零违规")
    void validDto_hasNoViolation() {
        Set<String> msgs = messages(valid());
        assertTrue(msgs.isEmpty(), "合法入参不应有违规，实际：" + msgs);
    }

    // ---------------- 单价：对齐 DECIMAL(12,2) ----------------

    @Test
    @DisplayName("单价：11 位整数（超 DECIMAL(12,2) 列宽）→ 「" + PRICE_DIGITS + "」")
    void price_tooManyIntegerDigits_isRejected() {
        ProductPublishDTO dto = valid();
        // 99999999999.99 = 11 位整数，比列宽多一位；只写 @DecimalMin 时它会一路撞库
        dto.setPrice(new BigDecimal("99999999999.99"));

        assertTrue(messages(dto).contains(PRICE_DIGITS),
                "超列宽的整数位必须在入口拦住，否则数据库报 1264、被兜底成 500。实际：" + messages(dto));
    }

    @Test
    @DisplayName("单价：3 位小数（超 DECIMAL(12,2) 精度）→ 「" + PRICE_DIGITS + "」")
    void price_tooManyFractionDigits_isRejected() {
        ProductPublishDTO dto = valid();
        dto.setPrice(new BigDecimal("1.234"));

        assertTrue(messages(dto).contains(PRICE_DIGITS),
                "小数位超出 DECIMAL(12,2) 的 2 位同样会被数据库拒绝。实际：" + messages(dto));
    }

    @Test
    @DisplayName("单价边界：9999999999.99（正好 10 位整数 + 2 位小数）应放行")
    void price_exactlyAtColumnLimit_isAccepted() {
        ProductPublishDTO dto = valid();
        dto.setPrice(new BigDecimal("9999999999.99"));

        assertFalse(messages(dto).contains(PRICE_DIGITS),
                "列宽上限本身是合法的，@Digits 的语义是「不超过」。实际：" + messages(dto));
    }

    @Test
    @DisplayName("单价边界：0.01（最小可分单位）应放行")
    void price_atMinimum_isAccepted() {
        ProductPublishDTO dto = valid();
        dto.setPrice(new BigDecimal("0.01"));

        assertFalse(messages(dto).contains(PRICE_POSITIVE),
                "1 分是合法价格，下界约束不该把它挡住。实际：" + messages(dto));
    }

    @Test
    @DisplayName("单价：0 / 负数 → 「" + PRICE_POSITIVE + "」")
    void price_notPositive_isRejected() {
        for (String bad : new String[]{"0.00", "-1.00"}) {
            ProductPublishDTO dto = valid();
            dto.setPrice(new BigDecimal(bad));

            assertTrue(messages(dto).contains(PRICE_POSITIVE),
                    "单价 " + bad + " 必须被拦住（否则订单金额会出现 0 或负数）。实际：" + messages(dto));
        }
    }

    // ---------------- 其余字段：逐项对齐列宽 ----------------

    @Test
    @DisplayName("商品名称：101 字被拒、100 字放行（对齐 VARCHAR(100)）")
    void productName_lengthBoundary() {
        ProductPublishDTO tooLong = valid();
        tooLong.setProductName(repeat('橙', 101));
        assertTrue(messages(tooLong).contains(NAME_TOO_LONG),
                "101 字必须被拦住，否则撞 VARCHAR(100) 报 Data too long。实际：" + messages(tooLong));

        ProductPublishDTO atLimit = valid();
        atLimit.setProductName(repeat('橙', 100));
        assertFalse(messages(atLimit).contains(NAME_TOO_LONG),
                "100 字是列宽上限，必须放行。实际：" + messages(atLimit));
    }

    @Test
    @DisplayName("商品品类 / 计量单位 / 产地 / 图片地址：超列宽一律被拒")
    void otherFields_lengthLimits() {
        ProductPublishDTO dto = valid();
        dto.setCategory(repeat('类', 31));      // VARCHAR(30)
        dto.setUnit(repeat('斤', 11));          // VARCHAR(10)
        dto.setOrigin(repeat('地', 51));        // VARCHAR(50)
        dto.setImageUrl("/" + repeat('a', 255)); // VARCHAR(255)

        Set<String> msgs = messages(dto);
        assertTrue(msgs.contains("商品品类不能超过 30 字"), "实际：" + msgs);
        assertTrue(msgs.contains("计量单位不能超过 10 字"), "实际：" + msgs);
        assertTrue(msgs.contains("产地不能超过 50 字"), "实际：" + msgs);
        assertTrue(msgs.contains("图片地址不能超过 255 字"), "实际：" + msgs);
    }

    @Test
    @DisplayName("多图数组：10 张被拒、9 张放行（业务上限 9）")
    void images_countLimit() {
        ProductPublishDTO ten = valid();
        ten.setImages(new ArrayList<>(Collections.nCopies(10, "/products/p01.jpg")));
        assertTrue(messages(ten).contains("最多上传 9 张图片"),
                "第 10 张图必须被拦住。实际：" + messages(ten));

        ProductPublishDTO nine = valid();
        nine.setImages(new ArrayList<>(Collections.nCopies(9, "/products/p01.jpg")));
        assertFalse(messages(nine).contains("最多上传 9 张图片"),
                "9 张是上限本身，必须放行。实际：" + messages(nine));
    }

    @Test
    @DisplayName("描述：超过 500 字被拒（TEXT 无列宽，按全项目统一口径）")
    void description_lengthLimit() {
        ProductPublishDTO dto = valid();
        dto.setDescription(repeat('好', 501));

        assertTrue(messages(dto).contains("商品描述不能超过 500 字"),
                "无边界长文本会拖慢列表查询。实际：" + messages(dto));
    }

    @Test
    @DisplayName("必填项为空白 → 报「请填写…」，且不叠加长度类提示")
    void blankRequiredFields() {
        ProductPublishDTO dto = valid();
        dto.setProductName("   ");
        dto.setCategory("");

        Set<String> msgs = messages(dto);
        assertTrue(msgs.contains("请填写商品名称"), "实际：" + msgs);
        assertTrue(msgs.contains("请选择商品品类"), "实际：" + msgs);
        assertFalse(msgs.contains(NAME_TOO_LONG),
                "空白值不该同时抱怨长度 —— 同一字段一次只报一条。实际：" + msgs);
    }

    @Test
    @DisplayName("可空字段为 null 时不应产生违规（编辑场景的「不修改」语义）")
    void nullOptionalFields_areAccepted() {
        ProductPublishDTO dto = new ProductPublishDTO();
        dto.setProductName("赣南脐橙");
        dto.setCategory("水果");
        dto.setPrice(new BigDecimal("29.90"));
        dto.setStock(0);
        // unit / description / origin / imageUrl / images 全部留空
        assertEquals(0, VALIDATOR.validate(dto).size(),
                "只在内容变更时才提交的字段置空是合法的，不该被拒。实际：" + messages(dto));
    }
}
