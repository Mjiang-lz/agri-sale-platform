package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址数据访问接口（t_address）。
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
