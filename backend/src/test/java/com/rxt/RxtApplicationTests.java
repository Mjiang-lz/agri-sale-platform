package com.rxt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试。默认使用 dev profile，需要本地 MySQL 可用。
 * 仅做结构校验时可加 @ActiveProfiles("test") 并改用 H2。
 */
@SpringBootTest
class RxtApplicationTests {

    @Test
    void contextLoads() {
    }
}
