package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问接口（t_role）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
