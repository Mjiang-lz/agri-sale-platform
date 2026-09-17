package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Answer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回答数据访问接口（t_answer）。
 */
@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {
}
