package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.FarmerProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 农户档案数据访问接口（t_farmer_profile）。
 */
@Mapper
public interface FarmerProfileMapper extends BaseMapper<FarmerProfile> {
}
