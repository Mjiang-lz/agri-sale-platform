package com.rxt.storage;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.PutObjectRequest;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.config.FileStorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
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
            throw new IllegalStateException("rxt.obs.enabled=true，但 access-key / secret-key 为空。"
                    + "密钥不得写入 application.yml，请通过环境变量或 application-local.yml 注入");
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
