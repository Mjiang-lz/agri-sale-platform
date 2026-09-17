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
 * 农户档案实体，对应表 t_farmer_profile。
 */
@Data
@TableName("t_farmer_profile")
public class FarmerProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案主键 */
    @TableId(value = "profile_id", type = IdType.AUTO)
    private Long profileId;

    /** 关联农户用户 */
    private Long userId;

    /** 主营农产品 */
    private String mainProduct;

    /** 经营规模描述 */
    private String scaleDesc;

    /** 信用与经营特征记录，作为智能匹配的输入特征 */
    private String creditRecord;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
