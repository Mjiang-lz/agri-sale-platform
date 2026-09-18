package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.service.DemandService;
import com.rxt.vo.DemandVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 求购需求接口。基础路径 /api/demand。
 * <p>登录鉴权(AuthServiceImpl)未实现，临时用 publisherId 参数传入；
 * 鉴权完成后改回 UserContext.currentUserId()。</p>
 */
@RestController
@RequestMapping("/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    /**
     * 发布求购需求。
     */
    @PostMapping("/publish")
    public Result<Long> publish(@RequestParam Long publisherId,
                                @RequestBody @Valid DemandPublishDTO dto) {
        return Result.success(demandService.publish(publisherId, dto));
    }

    /**
     * 分页查询求购需求列表。
     */
    @GetMapping("/list")
    public Result<IPage<DemandVO>> pageDemands(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) String region) {
        return Result.success(demandService.pageDemands(pageNum, pageSize, category, region));
    }

    /**
     * 求购需求详情。
     */
    @GetMapping("/{id}")
    public Result<DemandVO> detail(@PathVariable Long id) {
        return Result.success(demandService.detail(id));
    }

    /**
     * 编辑求购需求。
     */
    @PutMapping("/{id}")
    public Result<Void> updateDemand(@PathVariable Long id,
                                     @RequestParam Long publisherId,
                                     @RequestBody @Valid DemandPublishDTO dto) {
        demandService.updateDemand(publisherId, id, dto);
        return Result.success();
    }

    /**
     * 关闭求购需求。
     */
    @PostMapping("/{id}/close")
    public Result<Void> closeDemand(@PathVariable Long id,
                                    @RequestParam Long publisherId) {
        demandService.closeDemand(publisherId, id);
        return Result.success();
    }
}
