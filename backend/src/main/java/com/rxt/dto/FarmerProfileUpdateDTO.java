package com.rxt.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 农户经营档案维护入参。
 *
 * <p>字段为 null 表示「本次不修改」。</p>
 *
 * <p>注意：长度上限与 t_farmer_profile 的列定义保持一致 ——
 * main_product / scale_desc 均为 VARCHAR(50)，credit_record 为 TEXT。
 * 其中 scale_desc 仅 50 字符偏紧，如后续发现不够用，
 * 应加一条 migrations 脚本扩列，而不是只改这里的注解。</p>
 */
@Data
public class FarmerProfileUpdateDTO {

    /** 主营农产品 */
    @Size(max = 50, message = "主营产品长度不能超过 50 个字符")
    private String mainProduct;

    /** 经营规模描述 */
    @Size(max = 50, message = "经营规模描述不能超过 50 个字符")
    private String scaleDesc;

    /** 信用与经营特征记录，作为智能匹配的输入特征 */
    @Size(max = 1000, message = "信用记录长度不能超过 1000 个字符")
    private String creditRecord;
}
