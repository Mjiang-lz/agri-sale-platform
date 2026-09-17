package com.rxt.service;

import com.rxt.dto.FinanceApplyDTO;
import com.rxt.vo.FinanceApplyVO;
import java.util.List;

/**
 * 融资服务模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface FinanceService {

    /**
     * 提交融资意向与申请。
     */
    Long submitApply(Long farmerId, FinanceApplyDTO dto);

    /**
     * 查询本人融资申请列表。
     */
    List<FinanceApplyVO> listMyApplies(Long farmerId);

}
