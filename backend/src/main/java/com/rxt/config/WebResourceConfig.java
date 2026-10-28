package com.rxt.config;

import com.rxt.storage.FileStorageService;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把本地兜底存储目录映射成可访问的 URL 路径：
 * {@code /files/** → <工作目录>/<rxt.obs.local-dir>/}。
 *
 * <p>文件写到磁盘并不会自动有 URL，必须显式注册资源映射，浏览器才能取到图片。</p>
 *
 * <p><b>为什么新建一个类，而不是往 {@code WebMvcConfig} 里加：</b>
 * {@code WebMvcConfig} 是被多人改过的公共文件（鉴权收口时就动过），
 * 谁都想往里加一行，它就成了合并冲突的高发点。{@code WebMvcConfigurer} 可以有多个实现，
 * Spring 会把它们合并生效 —— 新增一个独立类功能完全等价，却把冲突面降到零。</p>
 *
 * <p><b>注册条件同样跟随 {@code rxt.obs.enabled=false}：</b>
 * OBS 模式下文件根本不在本地，这张映射是多余的；更重要的是
 * {@link com.rxt.security.PublicEndpoints} 里对应的免登录放行规则也是按同一个条件加的 ——
 * 两边条件一致，才不会出现「放行了却没内容」或「有内容却访问不了」的错位。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rxt.obs", name = "enabled", havingValue = "false", matchIfMissing = true)
public class WebResourceConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();

        // addResourceLocations 收的是 URL 字符串，且**必须以斜杠结尾**，
        // 否则 Spring 会把它当成「一个文件」而不是「一个目录」，表现是所有图片 404。
        // 目录可能尚未创建（首次启动），此时 Path#toUri 不会补斜杠，所以这里手动兜底。
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler(FileStorageService.LOCAL_PATH_PATTERN + "/**")
                .addResourceLocations(location);

        log.info("本地文件访问已映射：{}/** -> {}（免登录，见 PublicEndpoints）",
                FileStorageService.LOCAL_PATH_PATTERN, location);
    }
}
