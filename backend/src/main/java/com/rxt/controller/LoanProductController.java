package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.LoanProduct;
import com.rxt.service.LoanProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/finance")
public class LoanProductController {

    @Autowired
    private LoanProductService loanProductService;

    @GetMapping("/loan-products")
    public Result<Page<LoanProduct>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return Result.success(loanProductService.page(new Page<>(page, size)));
    }
}