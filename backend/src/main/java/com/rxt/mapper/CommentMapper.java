package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论数据访问接口（t_comment）。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
