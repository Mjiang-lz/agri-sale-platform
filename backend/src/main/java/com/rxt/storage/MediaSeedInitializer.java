package com.rxt.storage;

import com.rxt.config.FileStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 媒体种子铺底：把 jar 内 {@code seed-media/**} 复制到本地存储目录，让「种子图片」
 * 也住在存储层，而不是散落在前端的 public 目录里。
 *
 * <p><b>为什么要有这一步：</b>项目里三类图片原本各有各的家 ——
 * 专家头像与商品图是前端 {@code public/} 的静态文件（跟着前端构建产物走），
 * 知识配图是 OBS 上的绝对地址。结果是后台「图片资源管理」只能列出
 * {@code uploads/} 里那零星几个新上传的文件，其余全都管不着，
 * 「清理无人引用的孤儿文件」这个功能等于形同虚设。
 * 统一到存储层之后，那一页列出来的才是系统里真实存在的全部图片。</p>
 *
 * <p><b>为什么随 jar 走而不是让人手工上传：</b>种子图有 83 张、手工传一次不难，
 * 但下一次换服务器 / 重建环境就会忘 —— 这与「{@code sql/migrations/} 从来没进过部署包」
 * 是同一类事故：靠人记的步骤一定会在某一次漏掉。放进 jar 并在启动时自动铺底，
 * 就没有「记得传」这件事了。</p>
 *
 * <p><b>只在本地磁盘模式下铺底：</b>OBS 模式下不自动上传这 83 个文件到桶里。
 * 桶是共享资源，往里写应该是一次明确的操作，而不是每次启动的副作用；
 * 真要上 OBS 时由人工上传 + 配套迁移库里那批 URL。</p>
 *
 * <p><b>幂等：</b>已存在且大小一致就跳过。所以反复重启不会重复写盘，
 * 也不会覆盖管理员在后台删掉的文件之外的东西（删掉的文件会被重新铺回来 ——
 * 这是刻意的：种子数据本就该是确定的，想彻底移除某个种子图请改 jar 里的资源）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaSeedInitializer implements ApplicationRunner {

    /** jar 内种子目录（<b>刻意不放在 {@code static/} 下</b>，那样会被 Spring 直接当静态资源托管，
     * 于是又出现「图能从 /seed-media/xx.jpg 取到、但不在存储层列表里」的半吊子状态） */
    private static final String SEED_PATTERN = "classpath:/seed-media/**/*";

    /** 从资源 URI 里截取对象键时用的定位标记，见 {@link #objectKeyOf} */
    private static final String SEED_MARKER = "seed-media/";

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final FileStorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isEnabled()) {
            log.info("媒体种子：OBS 模式不铺底（桶内对象由人工管理），跳过");
            return;
        }

        Path root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        int copied = 0;
        int skipped = 0;
        int failed = 0;

        try {
            Resource[] resources = resolver.getResources(SEED_PATTERN);
            for (Resource resource : resources) {
                String objectKey = objectKeyOf(resource);
                if (objectKey == null || objectKey.isEmpty()) {
                    continue;
                }
                Path target = root.resolve(objectKey).normalize();
                // 对象键来自 jar 内部而非用户输入，但越界检查只花一次字符串比较，
                // 不写这一行的话，将来有人往 seed-media/ 里放个带 .. 的文件名就等于开了个写任意路径的口子
                if (!target.startsWith(root)) {
                    log.warn("媒体种子：对象键越界，已跳过 = {}", objectKey);
                    failed++;
                    continue;
                }
                if (isAlreadySeeded(resource, target)) {
                    skipped++;
                    continue;
                }
                try {
                    makeDirectory(target.getParent());
                    try (InputStream in = resource.getInputStream()) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    copied++;
                } catch (IOException e) {
                    // 单张失败不该让整个铺底中断：少一张图是页面上的一个裂图，
                    // 而抛异常会导致应用启动失败 —— 后者影响面大得多
                    log.warn("媒体种子：复制失败 objectKey = {}", objectKey, e);
                    failed++;
                }
            }
        } catch (IOException e) {
            log.error("媒体种子：枚举 jar 内种子目录失败，目录 = {}", SEED_PATTERN, e);
            return;
        }

        if (copied == 0 && failed == 0) {
            log.info("媒体种子：本地磁盘已铺好，本次无需新增（已存在 {} 张）", skipped);
        } else {
            log.info("媒体种子：本地磁盘铺底完成 — 新增 {} 张，已存在 {} 张，失败 {} 张，目录 = {}",
                    copied, skipped, failed, root);
        }
        // ★ 失败数不为 0 时必须再喊一遍。上面那行里三个数字挨在一起，
        //   「新增 0 张，失败 88 张」这种组合看着像正常结果，其实意味着**一张图都没铺上**。
        //   铺底失败的最终症状是页面上全是裂图，而那时没人会去翻这几行 INFO。
        if (failed > 0) {
            log.warn("媒体种子：⚠️ 有 {} 张种子图没能铺到 {}，"
                            + "页面上的对应位置会显示裂图。上面的 WARN 有具体原因，"
                            + "最常见的是磁盘写权限不足或同名文件挡住了目录。",
                    failed, root);
        }
    }

    /**
     * 建父目录，并把「同名的普通文件挡在那里」这个情况从「每张图各报一次」变成「报一次就好」。
     *
     * <p>为什么需要这个方法：{@link Files#createDirectories} 在目标路径**已存在但不是目录**时
     * 抛 {@link java.nio.file.FileAlreadyExistsException}。实测踩到过：{@code uploads/products}
     * 不知何时被建成了**零字节的普通文件**，于是 83 张图每一张都在建父目录这步炸掉，
     * 日志刷出 88 条栈，最后只剩一句「新增 0 张，失败 88 张」——
     * 症状是「83 张一张没铺」，但日志里既没有说清是哪个路径挡着，也看不出该怎么办。
     *
     * <p>所以这里：① 先判一次「存在且是目录」直接返回；② 存在但是普通文件时**只报一次**
     * 并抛一个说人话的异常，把「删掉那个文件」这个动作直接写进日志 ——
     * 与其让 83 条一样的栈刷屏，不如第一条就讲清楚。
     */
    private void makeDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        if (Files.exists(dir)) {
            throw new IOException(String.format(
                    "路径被一个非目录的文件挡住了：%s（%d 字节）。"
                            + "请删掉它后重启 —— 它不是本程序该写的位置。",
                    dir.toAbsolutePath(), Files.size(dir)));
        }
        Files.createDirectories(dir);
    }

    /**
     * 已铺好且大小一致就跳过。
     *
     * <p>比大小而不是比内容哈希：83 张图每次启动全量算 SHA-256 纯属浪费，
     * 而大小不同却同名的情况在种子目录里不会发生（真发生了也只是重新铺一次，
     * 结果是对的）。{@code contentLength()} 返回 {@code -1} 表示长度未知，
     * 此时一律当作「需要铺」——宁可多写一次，也不要在拿不到长度时误判成已就绪。</p>
     */
    private boolean isAlreadySeeded(Resource resource, Path target) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            long seedLength = resource.contentLength();
            return seedLength > 0 && Files.size(target) == seedLength;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从资源 URI 里取出对象键（相对 {@code seed-media/} 的那截）。
     *
     * <p>URI 形态随打包方式变化：开发时是 {@code file:/.../resources/seed-media/products/g01.jpg}，
     * 打成 jar 后是 {@code jar:file:/...app.jar!/BOOT-INF/classes/seed-media/products/g01.jpg}。
     * 两种都在 {@code seed-media/} 之后是同一截路径，所以按标记定位比解析 URI 稳。
     * 定位不到返回 {@code null}（跳过并计数），不抛异常。</p>
     */
    private String objectKeyOf(Resource resource) {
        String uri;
        try {
            uri = resource.getURI().toString();
        } catch (IOException e) {
            return null;
        }
        int index = uri.indexOf(SEED_MARKER);
        if (index < 0) {
            return null;
        }
        return uri.substring(index + SEED_MARKER.length());
    }
}