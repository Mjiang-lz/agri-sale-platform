package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.vo.DemandVO;

/**
 * 求购需求模块业务接口。
 */
public interface DemandService {

    /**
     * 发布求购需求。
     *
     * @param publisherId 发布人主键
     * @param dto         发布入参
     * @return 新建求购需求主键
     */
    Long publish(Long publisherId, DemandPublishDTO dto);

    /**
     * 分页查询求购需求列表。
     *
     * @param pageNum  当前页
     * @param pageSize 每页条数
     * @param category 品类筛选（可选）
     * @param region   地区筛选（可选）
     * @return 分页结果
     */
    IPage<DemandVO> pageDemands(long pageNum, long pageSize, String category, String region);

    /**
     * 求购需求详情。
     *
     * @param demandId 需求主键
     * @return 详情视图对象
     */
    DemandVO detail(Long demandId);

    /**
     * 编辑求购需求。
     *
     * @param publisherId 发布人主键（用于权限校验）
     * @param demandId    需求主键
     * @param dto         编辑入参
     */
    void updateDemand(Long publisherId, Long demandId, DemandPublishDTO dto);

    /**
     * 关闭（下架）求购需求。
     *
     * @param publisherId 发布人主键（用于权限校验）
     * @param demandId    需求主键
     */
    void closeDemand(Long publisherId, Long demandId);
}
