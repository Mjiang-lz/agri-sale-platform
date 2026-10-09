package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.HomeService;
import com.rxt.vo.HomeStatisticsVO;
import com.rxt.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页接口。基础路径 /api/home。首页内容对未登录用户开放，已在 WebMvcConfig 中放行。
 */
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 首页推荐商品。
     */
    @GetMapping("/products")
    public Result<List<ProductVO>> listHotProducts(@RequestParam(defaultValue = "8") int limit) {
        return Result.success(homeService.listHotProducts(limit));
    }

    /**
     * 首页最新农业知识。
     */
    @GetMapping("/knowledge")
    public Result<List<Knowledge>> listLatestKnowledge(@RequestParam(defaultValue = "6") int limit) {
        return Result.success(homeService.listLatestKnowledge(limit));
    }

    /**
     * 首页平台运行数据（在售货源 / 有效求购 / 已通过审核的知识及分类分布）。
     *
     * <p>官网落地页展示的是平台真实数据，不能用写死的演示数字。
     * 与其让前端自己拉列表再数一遍（口径一旦分散就会各算各的），
     * 不如由后端统计一次给出去。</p>
     *
     * <p>只返回统计量，不含任何用户信息，因此与上面两个接口一样对游客开放 ——
     * {@code GET /home/**} 已整体在免登录白名单内（见 {@code PublicEndpoints}），
     * 本接口无需再单独放行。</p>
     */
    @GetMapping("/stats")
    public Result<HomeStatisticsVO> statistics() {
        return Result.success(homeService.statistics());
    }
}
