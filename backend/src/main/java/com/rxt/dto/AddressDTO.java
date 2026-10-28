package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 编辑收货地址入参。
 *
 * <p>新增与编辑共用一个 DTO：字段完全一致，拆成两个类只会让两边慢慢漂移
 * （与 {@link ProductPublishDTO} 同一口径）。</p>
 *
 * <p><b>编辑按「整体覆盖」处理，不是部分更新</b> —— 本 DTO 里除 district 外全部字段
 * 都是 {@code @NotBlank}，前端提交的就是表单里的完整值。这与商品的「部分更新」语义
 * 不同是有意的：地址表单本来就是一次性填完的小表单，允许只传一半会让
 * 「清空某个字段」和「不改这个字段」变得无法区分。</p>
 *
 * <p><b>长度上限一律与 t_address 的列宽对齐</b>：注解比列宽宽松时，超长值会一路走到
 * JDBC 才抛 {@code Data too long for column}，被兜底成 500「系统繁忙」，联调时很难定位。
 * 改这里之前先核对 schema.sql 的列宽（consignee 50 / phone 20 / province 50 /
 * city 50 / district 50 / detail 200）。</p>
 */
@Data
public class AddressDTO {

    /** 收货人姓名 */
    @NotBlank(message = "请填写收货人姓名")
    @Size(max = 50, message = "收货人姓名不能超过 50 字")
    private String consignee;

    /**
     * 收货人手机号。
     *
     * <p>这里只做格式校验，不查重 —— 同一个人用同一手机号建两条地址是正常需求
     * （例如「家里」和「仓库」收件人电话相同）。</p>
     */
    @NotBlank(message = "请填写收货人手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请填写正确的手机号")
    private String phone;

    /** 省 / 直辖市 */
    @NotBlank(message = "请填写省份")
    @Size(max = 50, message = "省份不能超过 50 字")
    private String province;

    /** 市 */
    @NotBlank(message = "请填写城市")
    @Size(max = 50, message = "城市不能超过 50 字")
    private String city;

    /** 区 / 县。可为空 —— 部分乡镇地址不细分到区县 */
    @Size(max = 50, message = "区县不能超过 50 字")
    private String district;

    /** 详细地址（街道门牌） */
    @NotBlank(message = "请填写详细地址")
    @Size(max = 200, message = "详细地址不能超过 200 字")
    private String detail;

    /**
     * 是否设为默认地址：1 是，0 否。
     *
     * <p>不写 {@code @NotNull} 也不写 {@code @Min/@Max}：它在 Service 层被归一化成
     * 「等于 1 就是默认、其余一律按非默认处理」，传 null 与传 0 行为一致。
     * 这里只限制上界之外的取值不会有副作用，因此没必要用一个注解去挡
     * 「传了 7 怎么办」这种没有实际后果的输入。</p>
     */
    private Integer isDefault;
}
