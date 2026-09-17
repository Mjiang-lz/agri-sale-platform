package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.ExpertInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专家信息数据访问接口（t_expert）。
 */
@Mapper
public interface ExpertInfoMapper extends BaseMapper<ExpertInfo> {
}
