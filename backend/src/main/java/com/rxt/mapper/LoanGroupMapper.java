package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.LoanGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 联合贷款小组数据访问接口（t_loan_group）。
 */
@Mapper
public interface LoanGroupMapper extends BaseMapper<LoanGroup> {
}
