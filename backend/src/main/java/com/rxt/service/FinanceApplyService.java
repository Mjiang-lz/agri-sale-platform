package com.rxt.service;

import com.rxt.dto.FinanceApplyDTO;
import com.rxt.entity.FinanceApply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 为接口3做准备
import com.rxt.vo.UserVO;

import java.util.List;

public interface FinanceApplyService extends IService<FinanceApply> {
    // 接口2：融资意向提交
    void submitApply(Long userId, FinanceApplyDTO dto);

    // 接口3：我的融资进度查询
    Page<FinanceApply> getMyApply(Long userId, Integer page, Integer size);

    // 接口4：联合融资人智能推荐
    List<UserVO> recommendGuarantors(Long userId);
}