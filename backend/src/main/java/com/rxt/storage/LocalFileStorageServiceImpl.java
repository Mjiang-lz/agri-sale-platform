package com.rxt.storage;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地磁盘存储：把上传的图片写到 {@code rxt.obs.local-dir}，返回 {@code /api/files/...} 形式的地址。
 *
 * <p><b>定位：演示与本地联调的兜底实现，不是最终形态。</b>
 * 演示环境没有 OBS 的 AK/SK，若只留 OBS 一条路，「农户发布并上传图片」这条验收判据
 * 在本地根本演示不了。所以默认启用本实现，把整条链路（上传 → 拿到 URL → 发布商品 →
 * 详情页能看到图）先跑通；拿到密钥后把 {@code rxt.obs.enabled} 改成 true 即切到 OBS，
 * 业务代码一行不用动。</p>
 *
 * <p><b>访问路径为什么要额外注册一个资源映射：</b>
 * 文件写到磁盘后，浏览器要通过 URL 才能取到。映射关系注册在
 * {@link com.rxt.config.WebResourceConfig}，形如
 * {@code /files/** → <工作目录>/uploads/}。同时该路径必须在免登录白名单里 ——
 * 否则 {@code <img src>} 发出的请求（浏览器不会带 Authorization 头）会被拦成 401。</p>
 *
 * <p><b>注册条件用 {@code matchIfMissing = true}：</b>
 * 配置文件里万一漏写了 {@code enabled}，也应该落到「本地可用」这条路上，
 * 而不是两个实现都没注册、启动时注入失败。默认值要选「能跑起来」的那个。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rxt.obs", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties properties;

    /** 遍历本地存储目录的最大深度，见 {@link #list} */
    private static final int MAX_SCAN_DEPTH = 5;

    /** 存储根目录的绝对路径，启动时解析一次缓存下来 */
    private Path root;

    /**
     * 启动时把目录建好并打印绝对路径。
     *
     * <p>打印绝对路径是有意为之：{@code local-dir} 是相对路径，
     * 实际位置取决于「启动时的工作目录」（IDEA 里 Run 是模块根，打成 jar 后是启动目录）。
     * 联调时最常见的疑问就是「文件到底写到哪去了」，启动日志直接给答案。</p>
     */
    @PostConstruct
    void prepareRoot() {
        root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            // 目录建不出来 = 上传功能必然不可用。启动时就说清楚，别等到验收演示时才炸。
            throw new IllegalStateException("无法创建本地文件存储目录：" + root
                    + "，请检查 rxt.obs.local-dir 配置与目录写入权限", e);
        }
        log.info("文件存储：本地磁盘模式已启用，目录 = {}，访问前缀 = {}", root, LOCAL_URL_PREFIX);
    }

    @Override
    public StoredFile store(MultipartFile file, FileBizType bizType) {
        // 与 OBS 实现共用同一套校验与命名规则，切换存储不会让约束失效
        ImageFileValidator.ImageFormat format = ImageFileValidator.validate(file);
        String objectKey = ObjectKeys.build(bizType, format.extension());

        Path target = root.resolve(objectKey).normalize();
        // 纵深防御：objectKey 全部由服务端生成、不含客户端输入，正常不可能越界。
        // 但「路径拼接」是这个仓库里最容易出事的一类写法，留一道断言的成本近乎为零。
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "文件路径不合法");
        }

        try {
            // getParent 是年月目录，同样要确保存在（首次写入某个新月份时）
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                // 刻意不加 REPLACE_EXISTING：对象键是 UUID，正常不可能撞名。
                // 万一真撞上，宁可报错也不要静默覆盖别人的图。
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("本地文件写入失败，目标 = {}", target, e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "图片上传失败，请重新上传");
        }

        log.info("本地文件写入成功 bizType={} objectKey={} size={}B", bizType.name(), objectKey, file.getSize());
        return new StoredFile(LOCAL_URL_PREFIX + "/" + objectKey, objectKey, file.getSize(), "local");
    }

    /**
     * 列出本地 {@code uploads/} 下的文件。
     *
     * <p>深度限制为 5 层：本实现写出的对象键固定是 {@code 业务目录/年/月/文件名}（3 层），
     * 留两层余量给将来可能出现的子目录。用 {@code Integer.MAX_VALUE} 全量遍历没有意义，
     * 只会让一次误配置（比如把 local-dir 指到用户主目录）变成一次目录炸弹。</p>
     */
    @Override
    public List<StoredObject> list(String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.replace('\\', '/');
        try (Stream<Path> walk = Files.walk(root, MAX_SCAN_DEPTH)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> toStoredObject(path))
                    .filter(java.util.Objects::nonNull)
                    .filter(object -> object.objectKey().startsWith(normalizedPrefix))
                    .sorted(Comparator.comparing(StoredObject::objectKey))
                    .limit(MAX_LIST_SIZE)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // 列不出来通常是权限或目录被挪走。这里不抛 500：管理页拿不到列表应当看到空态 + 日志，
            // 而不是一个把整页打断的异常
            log.error("列出本地文件失败，目录 = {}，prefix = {}", root, prefix, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveSafely(objectKey);
        try {
            // deleteIfExists 而不是 delete：本方法的契约是幂等（见接口注释）
            if (Files.deleteIfExists(target)) {
                log.info("本地文件已删除 objectKey={}", objectKey);
            } else {
                log.info("本地文件不存在，视为删除成功 objectKey={}", objectKey);
            }
        } catch (IOException e) {
            log.error("本地文件删除失败 objectKey={}", objectKey, e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "删除失败，请稍后重试");
        }
    }

    @Override
    public String urlOf(String objectKey) {
        return LOCAL_URL_PREFIX + "/" + objectKey;
    }

    /**
     * 把对象键解析成根目录下的绝对路径，并挡住越界。
     *
     * <p>与 {@link #store} 里的那道断言同源：{@code store} 的 objectKey 由服务端生成，
     * 而 {@link #delete} 的 objectKey <b>来自客户端请求参数</b> —— 这里才是真正需要防御的地方。
     * 少了它，一个 {@code ../../application-local.yml} 就能把配置文件删掉。</p>
     */
    private Path resolveSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "缺少参数：objectKey");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "对象键不合法");
        }
        return target;
    }

    /** 磁盘文件 → {@link StoredObject}；相对路径统一转成 {@code /} 分隔，与对象键拼法一致 */
    private StoredObject toStoredObject(Path path) {
        try {
            String objectKey = root.relativize(path).toString().replace('\\', '/');
            long size = Files.size(path);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
            return new StoredObject(objectKey, urlOf(objectKey), size, lastModified);
        } catch (IOException e) {
            // 单个文件读不到属性不该让整个列表失败（并发删除时常见）
            log.warn("读取文件属性失败，已跳过：{}", path, e);
            return null;
        }
    }
}
