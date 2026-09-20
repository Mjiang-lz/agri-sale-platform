package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.entity.Demand;
import com.rxt.mapper.DemandMapper;
import com.rxt.service.DemandService;
import com.rxt.vo.DemandVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 求购需求模块业务实现。
 */
@Service
@RequiredArgsConstructor
public class DemandServiceImpl implements DemandService {

    private final DemandMapper demandMapper;

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
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException("求购需求不存在");
        }
        return convertToDemandVO(demand);
    }

    @Override
    public void updateDemand(Long publisherId, Long demandId, DemandPublishDTO dto) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new BizException("求购需求不存在");
        }
        if (!demand.getPublisherId().equals(publisherId)) {
            throw new BizException("无权修改他人发布的需求");
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
            throw new BizException("求购需求不存在");
        }
        if (!demand.getPublisherId().equals(publisherId)) {
            throw new BizException("无权关闭他人发布的需求");
        }
        demand.setStatus(0);
        demandMapper.updateById(demand);
    }

    private DemandVO convertToDemandVO(Demand demand) {
        DemandVO vo = new DemandVO();
        BeanUtils.copyProperties(demand, vo);
        return vo;
    }
}
