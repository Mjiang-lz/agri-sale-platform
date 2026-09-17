package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.LoanProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 贷款产品数据访问接口（t_loan_product）。
 */
@Mapper
public interface LoanProductMapper extends BaseMapper<LoanProduct> {
}
