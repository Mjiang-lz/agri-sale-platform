package com.rxt.service.impl;

import com.rxt.entity.LoanProduct;
import com.rxt.mapper.LoanProductMapper;
import com.rxt.service.LoanProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LoanProductServiceImpl extends ServiceImpl<LoanProductMapper, LoanProduct> implements LoanProductService {
}