package com.rxt.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：把上传的文件落到某个可长期访问的位置，并返回可访问地址。
 *
 * <p><b>为什么抽一层接口：</b>《概要设计说明书》§6 要求「转存对象存储并返回 URL」，
 * 但演示环境拿不到 OBS 的 AK/SK（{@code rxt.obs.enabled=false}），
 * 若把 OBS 调用直接写进 Controller，则「农户发布并上传图片」这条验收判据在本地根本演示不了。
 * 抽成接口后由 {@code rxt.obs.enabled} 二选一注册实现：</p>
 *
 * <ul>
 *   <li>{@link LocalFileStorageServiceImpl} —— 写本地磁盘，默认启用，让演示链路先跑通；</li>
 *   <li>{@link ObsFileStorageServiceImpl} —— 转存华为云 OBS，配上密钥后改一个开关即切换，
 *       业务代码零改动。</li>
 * </ul>
 *
 * <p><b>两个实现共用同一套校验与命名规则</b>（见 {@link ImageFileValidator} 与
 * {@link ObjectKeys}），所以「5MB 上限、格式白名单、重命名后转存」这些约束
 * 不会因为切换存储而失效。</p>
 */
public interface FileStorageService {

    /**
     * 本地兜底存储对外暴露的 URL 前缀，<b>含 context-path</b>。
     *
     * <p>形如 {@code /api/files} 的<b>相对</b>地址是有意为之：前端开发环境用 Vite 代理
     * {@code /api}，生产由 Nginx 同源转发，相对路径两边都对；写死 host 反而会在换环境时裂图。</p>
     */
    String LOCAL_URL_PREFIX = "/api/files";

    /**
     * 上面那个前缀<b>去掉 context-path</b> 之后的部分。
     *
     * <p>Spring MVC 的 {@code addResourceHandler} 与免登录白名单
     * （{@link com.rxt.security.PublicEndpoints}）匹配的都是「不含 context-path」的路径，
     * 所以要单独留一份。两处引用同一个常量，避免改了这头忘了那头的静默错位 ——
     * 那种错位的表现是「图片 401 / 404」，但根因在白名单里，很费时间。</p>
     */
    String LOCAL_PATH_PATTERN = "/files";

    /**
     * 存储一个文件。
     *
     * <p>实现方负责：校验（大小/格式）→ 生成对象键（重命名）→ 落存储 → 返回可访问地址。
     * 校验失败抛 {@link com.rxt.common.BizException}（400），落存储失败抛 500
     * 「图片上传失败，请重新上传」（《概要设计说明书》§6 对 OBS 调用的容错约定）。</p>
     *
     * @param file    上传的文件，不允许为空
     * @param bizType 业务归属，决定对象键的一级目录
     * @return 存储结果，含可访问 url
     */
    StoredFile store(MultipartFile file, FileBizType bizType);
}
