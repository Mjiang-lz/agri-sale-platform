package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 农户经营档案视图对象。
 *
 * <p>主营品类、经营规模与信用记录是「联合融资人智能推荐」的输入特征，
 * 由农户在个人中心自行维护。仅农户角色返回该对象，其余角色为 null。</p>
 */
@Data
public class FarmerProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案主键 */
    private Long profileId;

    /** 主营农产品 */
    private String mainProduct;

    /** 经营规模描述 */
    private String scaleDesc;

    /** 信用与经营特征记录 */
    private String creditRecord;
}
