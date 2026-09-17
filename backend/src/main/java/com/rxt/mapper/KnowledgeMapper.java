package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 农业知识数据访问接口（t_knowledge）。
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {
}
