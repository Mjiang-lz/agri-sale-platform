package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.DemandPublishDTO;
import com.rxt.security.UserContext;
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
 *
 * <p>发布/编辑/关闭属于「我的数据」，发布者身份一律从 {@link UserContext} 取当前登录用户，
 * 不再接受客户端传入 publisherId —— 该参数可被伪造，会让任何人以他人名义发布或修改需求。
 * 列表为公开接口，游客可访问。</p>
 */
@RestController
@RequestMapping("/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    /**
     * 发布求购需求。发布者身份取自登录态。
     */
    @PostMapping("/publish")
    public Result<Long> publish(@RequestBody @Valid DemandPublishDTO dto) {
        return Result.success(demandService.publish(UserContext.currentUserId(), dto));
    }

    /**
     * 分页查询求购需求列表。公开接口，游客可访问。
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
     *
     * <p>注：该路径与 {@code /demand/publish} 同属单段路径，无法只放行详情而拦住 publish，
     * 因此详情也要求登录。若产品上希望游客可看详情，
     * 请把路径改成 {@code /demand/detail/{id}}，再在 WebMvcConfig 中放行 {@code /demand/detail/**}。</p>
     */
    @GetMapping("/{id}")
    public Result<DemandVO> detail(@PathVariable Long id) {
        return Result.success(demandService.detail(id));
    }

    /**
     * 编辑求购需求。只能改自己发布的需求，归属校验在 service 层完成。
     */
    @PutMapping("/{id}")
    public Result<Void> updateDemand(@PathVariable Long id,
                                     @RequestBody @Valid DemandPublishDTO dto) {
        demandService.updateDemand(UserContext.currentUserId(), id, dto);
        return Result.success();
    }

    /**
     * 关闭求购需求。只能关闭自己发布的需求，归属校验在 service 层完成。
     */
    @PostMapping("/{id}/close")
    public Result<Void> closeDemand(@PathVariable Long id) {
        demandService.closeDemand(UserContext.currentUserId(), id);
        return Result.success();
    }
}
