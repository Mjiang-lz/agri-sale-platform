package com.rxt.storage;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectRequest;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.config.FileStorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 华为云 OBS 存储：把上传的图片 {@code PUT} 到对象存储，返回公网可访问地址。
 *
 * <p>对应《概要设计说明书》§6 的「公共 → 文件上传 → 转存对象存储并返回 URL」，
 * 以及与 OBS 的调用约定「PUT 对象上传（图片二进制、对象键）→ 可访问 URL，
 * 上传失败提示重新上传」。</p>
 *
 * <p><b>只有当 {@code rxt.obs.enabled=true} 时本类才会被注册。</b>
 * 与 {@link LocalFileStorageServiceImpl} 是同一个接口的两个实现，靠同一个开关互斥注册，
 * 因此容器里任何时刻只有一个 {@code FileStorageService}，调用方按接口注入即可。</p>
 *
 * <p><b>客户端在构造时创建、失败即启动失败：</b>
 * 密钥配错、endpoint 写错这类问题，如果拖到第一次上传才暴露，表现是「演示到一半传图失败」。
 * 构造器里就把配置校验掉，让应用启动阶段直接报出原因，比运行时猜要省事得多。</p>
 *
 * <p><b>本实现不注册任何资源的访问路径</b>：图片 URL 直接指向 OBS 域名，
 * 请求根本不经过后端，也就不会被登录拦截器拦下 —— 这正是对象存储相对本地存储的天然优势。
 * 前提是桶的读权限为「公共读」，否则要改用带签名的临时 URL；这一点需要运维/组长在桶上确认。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "rxt.obs", name = "enabled", havingValue = "true")
public class ObsFileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties properties;

    private final ObsClient obsClient;

    public ObsFileStorageServiceImpl(FileStorageProperties properties) {
        this.properties = properties;
        this.obsClient = createClient(properties);
        log.info("文件存储：OBS 模式已启用，endpoint = {}，bucket = {}",
                properties.getEndpoint(), properties.getBucket());
    }

    /**
     * 校验配置并创建 OBS 客户端。
     *
     * <p>用 {@link IllegalStateException} 而不是 {@code BizException}：
     * 这是「部署配置不对」，不是「用户请求不合法」。让它把启动顶掉，
     * 责任人（部署方）第一时间就知道要改什么，而不是被前端问「为什么传图报错」。</p>
     */
    private static ObsClient createClient(FileStorageProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("rxt.obs.enabled=true，但未配置 rxt.obs.endpoint");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("rxt.obs.enabled=true，但未配置 rxt.obs.bucket");
        }
        if (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            // 提示语必须点名**具体的变量名**：只说「用环境变量注入」等于给了方法没给钥匙孔，
            // 拿到报错的人还得回去翻 application.yml 才知道该 export 什么。
            throw new IllegalStateException("rxt.obs.enabled=true，但 access-key / secret-key 为空。"
                    + "密钥不得写入 application.yml。请在启动前注入："
                    + "export RXT_OBS_ACCESS_KEY=你的AK; export RXT_OBS_SECRET_KEY=你的SK"
                    + "（或写进 backend/src/main/resources/application-local.yml，该文件已在 .gitignore 中）");
        }
        ObsConfiguration configuration = new ObsConfiguration();
        configuration.setEndPoint(properties.getEndpoint());
        // 虚拟主机风格（bucket.endpoint），与下方 publicUrl 拼出来的地址形式保持一致
        configuration.setPathStyle(false);
        configuration.setConnectionTimeout(10_000);
        return new ObsClient(properties.getAccessKey(), properties.getSecretKey(), configuration);
    }

    @Override
    public StoredFile store(MultipartFile file, FileBizType bizType) {
        // 校验与命名规则同本地实现完全一致：换存储不影响业务约束
        ImageFileValidator.ImageFormat format = ImageFileValidator.validate(file);
        String objectKey = ObjectKeys.build(bizType, format.extension());

        try (InputStream in = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            // Content-Type 取识别结果，不取客户端上传头 —— 让浏览器按图片渲染，
            // 而不是按调用方声明的（可能是被篡改的）类型处理
            metadata.setContentType(format.contentType());
            metadata.setContentLength(file.getSize());

            PutObjectRequest request = new PutObjectRequest(properties.getBucket(), objectKey, in);
            request.setMetadata(metadata);
            obsClient.putObject(request);
        } catch (ObsException e) {
            // SDK 异常里带着 OBS 返回的状态码与错误码，是排查「密钥错 / 桶不存在 / 权限不足」的关键线索，
            // 必须落日志；但对外只回一句可操作的提示，不外泄服务端细节
            log.error("OBS 上传失败 objectKey={} status={} errorCode={} errorMessage={}",
                    objectKey, e.getResponseCode(), e.getErrorCode(), e.getErrorMessage(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "图片上传失败，请重新上传");
        } catch (IOException e) {
            log.error("读取上传文件流失败 objectKey={}", objectKey, e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "图片上传失败，请重新上传");
        }

        String url = publicUrl(objectKey);
        log.info("OBS 上传成功 bizType={} objectKey={} size={}B", bizType.name(), objectKey, file.getSize());
        return new StoredFile(url, objectKey, file.getSize(), "obs");
    }

    /**
     * 拼出对象的公网访问地址：{@code https://<bucket>.<endpoint>/<objectKey>}。
     */
    private String publicUrl(String objectKey) {
        return "https://" + properties.getBucket() + "." + endpointHost() + "/" + objectKey;
    }

    @Override
    public String urlOf(String objectKey) {
        return publicUrl(objectKey);
    }

    /**
     * 列出桶内对象，按对象键升序。
     *
     * <p>单次请求 {@code maxKeys} 设成上限值；万一桶里对象更多（{@code isTruncated=true}），
     * 打 WARN 后截断返回，<b>不做翻页</b> —— 理由见 {@link FileStorageService#list}。
     * 这里刻意不沿 {@code nextMarker} 循环：那会把「偶发的一次全量遍历」变成
     * 「每开一次管理页就把整个桶拉一遍」，而这个页面的用途是清理，不是审计。</p>
     */
    @Override
    public List<StoredObject> list(String prefix) {
        ListObjectsRequest request = new ListObjectsRequest(properties.getBucket());
        if (StringUtils.hasText(prefix)) {
            request.setPrefix(prefix);
        }
        request.setMaxKeys(MAX_LIST_SIZE);

        try {
            ObjectListing listing = obsClient.listObjects(request);
            if (listing.isTruncated()) {
                log.warn("OBS 对象数超过上限 {}，列表已截断（prefix={}）。"
                                + "按前缀分批查询，或改用对象存储的生命周期规则做归档",
                        MAX_LIST_SIZE, prefix);
            }
            List<StoredObject> result = new ArrayList<>();
            for (ObsObject object : listing.getObjects()) {
                result.add(toStoredObject(object));
            }
            return result;
        } catch (ObsException e) {
            // 同 store()：SDK 异常里带着状态码与错误码，落日志；对外给可操作提示
            log.error("OBS 列举对象失败 prefix={} status={} errorCode={} errorMessage={}",
                    prefix, e.getResponseCode(), e.getErrorCode(), e.getErrorMessage(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "读取图片列表失败，请稍后重试");
        }
    }

    /**
     * 删除对象。
     *
     * <p>OBS 的 DELETE 本身对不存在的对象就返回 204（幂等），与接口契约天然一致，
     * 无需额外判断。</p>
     */
    @Override
    public void delete(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "缺少参数：objectKey");
        }
        // 挡住 ../ 之类的越界键：OBS 侧不会真的删到别处，但放行一个明显不合法的键
        // 会让本地实现与 OBS 实现行为分裂（本地会 400、OBS 会静默成功）
        if (objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "对象键不合法");
        }
        try {
            obsClient.deleteObject(properties.getBucket(), objectKey);
            log.info("OBS 对象已删除 objectKey={}", objectKey);
        } catch (ObsException e) {
            log.error("OBS 删除对象失败 objectKey={} status={} errorCode={} errorMessage={}",
                    objectKey, e.getResponseCode(), e.getErrorCode(), e.getErrorMessage(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "删除失败，请稍后重试");
        }
    }

    /** OBS 对象 → {@link StoredObject}；大小与时间取不到时用 0 / null 兜底，不让列表失败 */
    private StoredObject toStoredObject(ObsObject object) {
        String objectKey = object.getObjectKey();
        ObjectMetadata metadata = object.getMetadata();
        long size = metadata == null || metadata.getContentLength() == null
                ? 0L : metadata.getContentLength();
        Date lastModified = metadata == null ? null : metadata.getLastModified();
        LocalDateTime modified = lastModified == null ? null
                : LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault());
        return new StoredObject(objectKey, publicUrl(objectKey), size, modified);
    }

    /** 取 endpoint 的纯主机名：允许配置里带 {@code https://} 前缀或结尾斜杠，这里统一抹掉 */
    private String endpointHost() {
        return properties.getEndpoint()
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
    }

    /**
     * 关闭客户端，释放其内部的连接池与线程。
     *
     * <p>Spring 销毁 Bean 时调用；漏掉这一步会在「重启后端」时留下未回收的连接
     * （开发期反复重启尤其明显）。</p>
     */
    @PreDestroy
    void shutdown() {
        try {
            obsClient.close();
            log.info("OBS 客户端已关闭");
        } catch (IOException e) {
            log.warn("关闭 OBS 客户端时出现异常", e);
        }
    }
}
