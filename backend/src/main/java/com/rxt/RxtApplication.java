package com.rxt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基于数字经济的农产品融销一体平台 —— 后端启动类。
 */
@SpringBootApplication
@MapperScan("com.rxt.mapper")
public class RxtApplication {

    public static void main(String[] args) {
        SpringApplication.run(RxtApplication.class, args);
    }
}
