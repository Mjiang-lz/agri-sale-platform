package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知数据访问接口（t_notification）。
 *
 * <p>不需要自定义方法：「标记全部已读」用 {@code LambdaUpdateWrapper} 一次 UPDATE 表达，
 * 不必手写 SQL（与 {@code AddressServiceImpl.clearOtherDefaults} 同一做法）。
 * 未读数走 {@code selectCount}，命中 {@code (user_id, is_read, create_time)} 索引前缀。</p>
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
