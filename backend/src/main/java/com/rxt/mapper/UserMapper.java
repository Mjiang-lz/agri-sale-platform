package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口（t_user）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
