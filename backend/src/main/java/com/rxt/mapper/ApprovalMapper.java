package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Approval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录数据访问接口（t_approval）。
 */
@Mapper
public interface ApprovalMapper extends BaseMapper<Approval> {
}
