package com.rxt.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布 / 编辑求购需求入参。
 *
 * <p><b>为什么每个字段都要有上限约束</b>（对齐 {@code t_demand} DDL 列宽）：
 * 没有约束时，超长或越界的数据会一路穿过 Controller 撞到数据库，在严格模式（本机
 * {@code sql_mode} 含 {@code STRICT_TRANS_TABLES}）下抛 DataIntegrityViolation，
 * 被兜底分支回成 {@code 500 系统繁忙} —— 而这其实是「调用方把参数填错了，
 * 改一下就能过」，应该是 {@code 400} 并说清哪个字段不对。
 * 数值无边界则会静默写入无意义数据（如 {@code quantity = -3}）。</p>
 *
 * <p>每一项的上限都取自 {@code db/schema.sql} 的 {@code t_demand} 定义，改列宽时请同步改这里。</p>
 */
@Data
public class DemandPublishDTO {

    /** 求购品类。DDL：{@code category VARCHAR(30)} */
    @NotBlank(message = "请填写求购品类")
    @Size(max = 30, message = "求购品类不能超过 30 字")
    private String category;

    /**
     * 求购数量。DDL：{@code quantity INT}
     *
     * <p>INT 本身放得下负数，但「求购 -3 斤」没有业务含义，且会污染列表与统计，
     * 故在下界拦住。</p>
     */
    @NotNull(message = "请填写求购数量")
    @Min(value = 1, message = "求购数量至少为 1")
    private Integer quantity;

    /** 计量单位。DDL：{@code unit VARCHAR(10)} */
    @Size(max = 10, message = "计量单位不能超过 10 字")
    private String unit;

    /**
     * 期望单价。DDL：{@code expect_price DECIMAL(12,2)}
     *
     * <p>{@code @Digits} 与列定义严格对应：DECIMAL(12,2) 即「10 位整数 + 2 位小数」，
     * 超过这个精度数据库会直接报错。可空（表示面议），非空时必须为正。</p>
     */
    @DecimalMin(value = "0.01", message = "期望单价必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "期望单价最多 10 位整数、2 位小数")
    private BigDecimal expectPrice;

    /**
     * 需求补充说明。DDL：{@code description TEXT}
     *
     * <p>TEXT 无列宽上限，这里按全项目统一口径限 500 字
     * （与商品描述、评论、问答保持一致），避免无边界长文本进库。</p>
     */
    @Size(max = 500, message = "需求补充说明不能超过 500 字")
    private String description;

    /** 收货地区。DDL：{@code region VARCHAR(50)} */
    @NotBlank(message = "请填写收货地区")
    @Size(max = 50, message = "收货地区不能超过 50 字")
    private String region;

    /**
     * 需求有效期。DDL：{@code valid_until DATETIME}（NOT NULL）
     *
     * <p><b>为什么这里要拦"必须是未来时间"和"不超过 90 天"</b>：
     * 需求 §2.7.3 UC-TRD-02 的约束条件写明「期望价格为正数；有效期不超过 90 天」，
     * 扩展流程又要求「需求超过有效期后系统自动置为失效」。若允许填一个过去的时间，
     * 发布接口会返回成功，但这条需求立刻被列表的 {@code valid_until >= NOW()}
     * 过滤掉 —— 用户看到"发布成功"却在列表里找不到，看起来像发布失败
     * （契约文档 §1.2 推论 2 记过这个坑）。所以要在发布时拦住，而不是等读取时消失。</p>
     *
     * <p><b>为什么不能写在 DDL 里</b>：MySQL 对 {@code CHECK} 约束在 8.0.16 之前
     * 是「解析但不生效」，5.7.x 上即使写了也不会拦任何数据（本项目目标库为 5.7.x），
     * 实测 {@code t_demand} 上 CHECK 约束数为 0。因此这条约束只能落在应用层。</p>
     */
    @NotNull(message = "请填写需求有效期")
    @Future(message = "需求有效期必须晚于当前时间")
    private LocalDateTime validUntil;

    /** 联系方式。DDL：{@code contact VARCHAR(50)} */
    @NotBlank(message = "请填写联系方式")
    @Size(max = 50, message = "联系方式不能超过 50 字")
    private String contact;

    /**
     * 有效期上限 90 天（需求 §2.7.3 UC-TRD-02 约束条件）。
     *
     * <p>{@code validUntil} 为 {@code null} 时返回 {@code true}，把"必填"交给
     * {@link NotNull} 单独报错 —— 避免同一个字段同时抛出「请填写需求有效期」和
     * 「不能超过 90 天」两条互相矛盾的提示。</p>
     *
     * <p>{@code @JsonIgnore} 是必需的：方法名符合 JavaBean getter 命名规则，
     * 不加这个注解会被 Jackson 当成一个名为 {@code validUntilWithin90Days}
     * 的属性参与序列化，从而多出一个调用方看不懂的字段。</p>
     */
    @JsonIgnore
    @AssertTrue(message = "需求有效期不能超过 90 天")
    public boolean isValidUntilWithin90Days() {
        if (validUntil == null) {
            return true;
        }
        return !validUntil.isAfter(LocalDateTime.now().plusDays(90));
    }
}
