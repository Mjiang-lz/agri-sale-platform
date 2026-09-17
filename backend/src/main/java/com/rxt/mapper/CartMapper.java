package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Cart;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车数据访问接口（t_cart）。
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}
