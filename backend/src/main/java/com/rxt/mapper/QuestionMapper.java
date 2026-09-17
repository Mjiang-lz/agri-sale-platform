package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提问数据访问接口（t_question）。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
