package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Demand;
import org.apache.ibatis.annotations.Mapper;

/**
 * 求购需求数据访问接口（t_demand）。
 */
@Mapper
public interface DemandMapper extends BaseMapper<Demand> {
}
