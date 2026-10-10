package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 收货地址实体，对应表 t_address。
 *
 * <p>与 {@link Order} 的关系是「刻意的冗余」：订单存的是下单那一刻的收货信息快照，
 * 不指向本表主键。地址被改被删都不该影响历史订单里的收货记录 ——
 * 详见 migrations/2026-10-10_01_address_and_order_snapshot.sql 的头部说明。</p>
 */
@Data
@TableName("t_address")
public class Address implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 地址主键 */
    @TableId(value = "address_id", type = IdType.AUTO)
    private Long addressId;

    /** 所属用户。不接受客户端传入，一律取自登录态 */
    private Long userId;

    /** 收货人姓名 */
    private String consignee;

    /** 收货人手机号 */
    private String phone;

    /** 省 / 直辖市 */
    private String province;

    /** 市 */
    private String city;

    /** 区 / 县，可为空（部分乡镇地址不细分到区县） */
    private String district;

    /** 详细地址（街道门牌） */
    private String detail;

    /** 是否默认地址：1 是，0 否 */
    private Integer isDefault;

    /** 创建时间，由 MybatisMetaObjectHandler 自动填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，由 MybatisMetaObjectHandler 自动填充 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
