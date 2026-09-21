package com.rxt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储（华为云 OBS）配置，绑定 {@code application.yml} 的 {@code rxt.obs} 段。
 *
 * <p><b>{@link #enabled} 是本地兜底与云端存储的切换开关：</b></p>
 * <ul>
 *   <li>{@code false}（默认）→ 文件写本地磁盘，URL 形如 {@code /api/files/...}。
 *       演示环境没有 OBS 密钥时靠它跑通「农户发布并上传图片」这条验收链路。</li>
 *   <li>{@code true} → 走 {@link com.rxt.storage.ObsFileStorageServiceImpl} 转存对象存储，
 *       URL 为 OBS 公网地址。需要同时配好 {@code access-key} / {@code secret-key}。</li>
 * </ul>
 *
 * <p><b>为什么不把开关做成「启动时二选一注册 Bean」而是放在配置类里：</b>
 * 两个实现都用 {@code @ConditionalOnProperty} 按同一个 {@code enabled} 值注册，
 * 同一时刻容器里只会有一个 {@link com.rxt.storage.FileStorageService} 实例，
 * 调用方注入接口即可，不需要在任何地方判断当前是哪种存储。</p>
 *
 * <p><b>密钥不入版本库</b>：{@code application.yml} 里的 {@code access-key} / {@code secret-key}
 * 一律留空，真实密钥通过环境变量或 {@code application-local.yml}（已在 .gitignore 中）注入 ——
 * 《概要设计说明书》§8.3 要求「配置文件中的数据库口令、对象存储密钥与 JWT 密钥与代码分离」。</p>
 *
 * @see com.rxt.storage.FileStorageService
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rxt.obs")
public class FileStorageProperties {

    /** 是否启用 OBS 转存。false 时走本地磁盘兜底（演示环境默认值） */
    private boolean enabled = false;

    /** OBS 接入点，如 obs.cn-north-4.myhuaweicloud.com */
    private String endpoint;

    /** 桶名 */
    private String bucket;

    /** 访问密钥 AK。留空表示未配置，仅为本地兜底模式服务 */
    private String accessKey;

    /** 私有密钥 SK。留空表示未配置，仅为本地兜底模式服务 */
    private String secretKey;

    /**
     * 本地兜底存储目录：相对「启动时的工作目录」。
     *
     * <p>IDEA 里直接 Run 时工作目录是模块根（{@code backend/}），所以文件会落在
     * {@code backend/uploads/}；该目录已在仓库 .gitignore 中忽略，不会被误提交。</p>
     */
    private String localDir = "uploads";
}
