package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.AuditRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核记录数据访问接口（t_audit）。
 */
@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecord> {
}
