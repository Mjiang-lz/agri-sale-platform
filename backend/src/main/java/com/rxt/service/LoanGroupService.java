package com.rxt.service;

import com.rxt.dto.LoanGroupDTO;
import com.rxt.vo.LoanGroupCreateVO;

/**
 * 联合贷款小组业务接口（REQ-08）。
 *
 * <p>成员关系由成员自己的 {@code t_finance_apply.group_id} 反向表达，
 * 不单独建成员表（见 {@code db/data.sql} 第 15 节）。</p>
 */
public interface LoanGroupService {

    /**
     * 组建联合贷款小组。
     *
     * @param userId 发起人（组长）用户主键，取自登录态
     * @param dto    建组入参，可携带被推荐的联保候选人
     * @return 建组结果：谁成功入组、谁被跳过及原因
     */
    LoanGroupCreateVO createGroup(Long userId, LoanGroupDTO dto);

    /**
     * 加入指定小组。
     *
     * @param groupId 小组主键
     * @param userId  加入者用户主键，取自登录态
     */
    void joinGroup(Long groupId, Long userId);

    /**
     * 退出指定小组。组长退出即解散整个小组。
     *
     * @param groupId 小组主键
     * @param userId  退出者用户主键，取自登录态
     */
    void quitGroup(Long groupId, Long userId);
}
