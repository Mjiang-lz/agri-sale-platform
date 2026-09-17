package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.HomeService;
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
}
