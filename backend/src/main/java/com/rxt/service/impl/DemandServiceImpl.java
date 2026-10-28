package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.entity.Demand;
import com.rxt.mapper.DemandMapper;
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
 * <p>读路径（{@link #pageDemands} / {@link #detail}）在查询前会先经
 * {@link #expireOverdueDemands()} 校正「已过期仍是有效状态」的行，
 * 使调用方看到的状态与「到期即失效」的需求口径一致。理由与边界见该私有方法说明。</p>
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
        // 先收口「已过期但状态仍是 1」的行，再查询 —— 否则「我的发布」按 publisher_id 直接查时，
        // 过期需求会一直显示为有效（见契约文档 §6.6）。命中 0 行时只是一条空 UPDATE。
        expireOverdueDemands();
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
        expireOverdueDemands();
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

    /**
     * 把「已过期（{@code valid_until < NOW()}）但仍停留在有效状态」的求购置为已关闭。
     *
     * <p><b>为什么用惰性收口，而不是加一个 {@code @Scheduled} 常驻定时任务</b>：
     * 《详细设计说明书》§10.2.5 对本模块有明文设计立场 ——
     * 「在明确需要之前不引入调度器……这里真正缺的只是『到期后把 status 置为 0』
     * 这一步状态变更；在该状态变更明确需要之前，不为此引入常驻调度组件」。
     * 也就是说这里缺的是<b>状态变更本身</b>，而不是「必须由调度器完成的状态变更」。
     * 在读取路径上收口，既补上了这一步，又不与上述立场冲突。</p>
     *
     * <p><b>为什么是本类的一个私有方法，而不是独立成一个类</b>：它的调用点只有本类的
     * 列表与详情两处，没有第二个调用方 —— 独立成类只多一层跳转，反而把
     * 「谁在什么时候校正状态」这句话从读方法上挪走了。收口性并未损失：SQL 仍然只有一处
     * （{@code DemandMapper.expireOverdue}），不存在两份口径慢慢漂开的风险。</p>
     *
     * <p><b>惰性是否满足「自动失效」的验收口径</b>：所有会展示 {@code status} 的出口
     * （列表、详情，以及将来「我的发布」的读取）都汇入这里，因此对调用方而言，
     * 拿到的状态永远已经校正过。<b>已知边界</b>：若长期无人访问，库里的 {@code status}
     * 会一直停在 1 —— 这是惰性方案的固有特性，也是它的优点（无请求时不做无用功）。
     * 若要求「无人访问时库里也必须是 0」，那就必须引入调度器，属与 §10.2.5 立场冲突的
     * 决策，需组长确认后再改。</p>
     *
     * <p><b>为什么这里没有 {@code @Transactional}</b>：单条 UPDATE 本身已是原子的；
     * 且私有方法上的 {@code @Transactional} 不会被 Spring AOP 代理拦截，写了也是假的。
     * 若将来要扩展成多条写语句，请把它提升为 {@code DemandService} 上的公开方法并加事务，
     * 而不是给私有方法贴注解。</p>
     *
     * @return 本次置为失效的行数；0 = 当前没有到期需求（绝大多数请求的常态）
     */
    private int expireOverdueDemands() {
        return demandMapper.expireOverdue();
    }

    private DemandVO convertToDemandVO(Demand demand) {
        DemandVO vo = new DemandVO();
        BeanUtils.copyProperties(demand, vo);
        return vo;
    }
}
