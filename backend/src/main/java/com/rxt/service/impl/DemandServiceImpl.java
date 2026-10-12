package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.entity.Demand;
import com.rxt.mapper.DemandMapper;
import com.rxt.service.DemandExpireService;
import com.rxt.service.DemandService;
import com.rxt.vo.DemandVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 求购需求模块业务实现。
 *
 * <p>读路径（{@link #pageDemands} / {@link #detail}）会先经
 * {@link DemandExpireService#expireOverdue()} 校正「已过期仍是有效状态」的行，
 * 使调用方看到的状态与「到期即失效」的需求口径一致。理由与边界见该类说明。</p>
 */
@Service
@RequiredArgsConstructor
public class DemandServiceImpl implements DemandService {

    private final DemandMapper demandMapper;

    private final DemandExpireService demandExpireService;

    @Override
    public Long publish(Long publisherId, DemandPublishDTO dto) {
        Demand demand = new Demand();
        demand.setPublisherId(publisherId);
        demand.setCategory(dto.getCategory());
        demand.setQuantity(dto.getQuantity());
        demand.setUnit(dto.getUnit());
        demand.setExpectPrice(dto.getExpectPrice());
        demand.setDescription(dto.getDescription());
        demand.setRegion(dto.getRegion());
        demand.setValidUntil(dto.getValidUntil());
        demand.setContact(dto.getContact());
        demand.setStatus(1);
        demandMapper.insert(demand);
        return demand.getDemandId();
    }

    @Override
    public IPage<DemandVO> pageDemands(long pageNum, long pageSize, String category, String region) {
        // 先收口「已过期但状态仍是 1」的行，再查询 —— 否则「我的发布」按 publisher_id 直接查时，
        // 过期需求会一直显示为有效（见契约文档 §6.6）。命中 0 行时只是一条空 UPDATE。
        demandExpireService.expireOverdue();
        Page<Demand> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Demand> wrapper = new LambdaQueryWrapper<>();
        // 只展示有效且未过期的求购
        wrapper.eq(Demand::getStatus, 1)
               .ge(Demand::getValidUntil, LocalDateTime.now());
        if (StringUtils.hasText(category)) {
            wrapper.like(Demand::getCategory, category);
        }
        if (StringUtils.hasText(region)) {
            wrapper.like(Demand::getRegion, region);
        }
        wrapper.orderByDesc(Demand::getCreateTime);
        Page<Demand> demandPage = demandMapper.selectPage(page, wrapper);
        return demandPage.convert(this::convertToDemandVO);
    }

    @Override
    public DemandVO detail(Long demandId) {
        // 详情是列表之外唯一能读到单条状态的出口（也是将来「我的发布」详情的数据来源），
        // 同样先校正再读，保证返回的 status 不是过期后残留的旧值。
        demandExpireService.expireOverdue();
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "求购需求不存在");
        }
        return convertToDemandVO(demand);
    }

    @Override
    public void updateDemand(Long publisherId, Long demandId, DemandPublishDTO dto) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "求购需求不存在");
        }
        if (!demand.getPublisherId().equals(publisherId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权修改他人发布的需求");
        }
        demand.setCategory(dto.getCategory());
        demand.setQuantity(dto.getQuantity());
        demand.setUnit(dto.getUnit());
        demand.setExpectPrice(dto.getExpectPrice());
        demand.setDescription(dto.getDescription());
        demand.setRegion(dto.getRegion());
        demand.setValidUntil(dto.getValidUntil());
        demand.setContact(dto.getContact());
        demandMapper.updateById(demand);
    }

    @Override
    public void closeDemand(Long publisherId, Long demandId) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "求购需求不存在");
        }
        if (!demand.getPublisherId().equals(publisherId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权关闭他人发布的需求");
        }
        demand.setStatus(0);
        demandMapper.updateById(demand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDemand(Long publisherId, Long demandId) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "求购需求不存在");
        }
        // 归属校验与 updateDemand / closeDemand 同一口径：不存在 404、非本人 403
        if (!demand.getPublisherId().equals(publisherId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权删除他人发布的需求");
        }
        // 求购不参与下单链路，全库无外键指向 t_demand，直接删行即可（理由见 DemandService#deleteDemand）
        demandMapper.deleteById(demandId);
    }

    private DemandVO convertToDemandVO(Demand demand) {
        DemandVO vo = new DemandVO();
        BeanUtils.copyProperties(demand, vo);
        return vo;
    }
}
