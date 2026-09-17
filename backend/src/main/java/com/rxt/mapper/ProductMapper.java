package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品货源数据访问接口（t_product）。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
