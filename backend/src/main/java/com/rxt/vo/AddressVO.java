package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 收货地址视图对象。
 *
 * <p>比实体多一个 {@code fullAddress}：省市区与详细地址拼好的整串。
 * 订单列表、结算页、订单详情都要展示「成吉思汗大街 xx 号」这种一行文本，
 * 让每个消费方各自拼一遍（还要各自处理 district 为 null 时不出现「null」字样）
 * 是重复且容易漏的，统一在服务端拼好。</p>
 */
@Data
public class AddressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long addressId;

    private String consignee;

    private String phone;

    private String province;

    private String city;

    /** 可能为空，拼 fullAddress 时自动跳过 */
    private String district;

    private String detail;

    /** 是否默认地址：1 是，0 否 */
    private Integer isDefault;

    /** 省 + 市 + 区县 + 详细地址，区县为空时不出现多余空格 */
    private String fullAddress;
}
