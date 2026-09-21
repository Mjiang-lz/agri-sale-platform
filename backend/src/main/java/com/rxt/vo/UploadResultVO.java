package com.rxt.vo;

import com.rxt.storage.FileBizType;
import com.rxt.storage.StoredFile;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传结果视图对象。
 *
 * <p>对应《概要设计说明书》§6「文件上传 → 转存对象存储并返回 URL」的返回值。</p>
 *
 * <p><b>一次请求只返回一个文件：</b>「商品图片 1～9 张」由前端多次调用得到多个 URL
 * 再组装进 {@code images} 数组。这样做的原因是错误语义干净 ——
 * 批量接口一旦第 3 张失败，前 2 张已经落库，前端要处理「部分成功」这种最麻烦的状态；
 * 单个上传则天然是全成功或全失败。Element Plus 的 {@code el-upload}
 * 默认也是「一个文件一个请求」，前端不需要额外适配。</p>
 *
 * <p><b>不回显客户端传来的文件名：</b>那是不可信输入，回显会让它进入日志、页面等下游。
 * 需要标识文件时用 {@link #objectKey}，它是服务端生成的。</p>
 */
@Data
public class UploadResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 可访问地址，直接放进 {@code <img src>} 即可。
     *
     * <p>本地兜底模式是相对路径 {@code /api/files/...}；OBS 模式是绝对地址
     * {@code https://<bucket>.<endpoint>/...}。两种形式浏览器都能直接用，
     * 前端不需要按模式分支处理。</p>
     */
    private String url;

    /** 对象键（存储侧相对路径），如 {@code product/2026/09/<uuid>.jpg}；排查问题时按它检索 */
    private String objectKey;

    /** 文件字节数 */
    private Long size;

    /** 实际承载存储的实现：{@code local} 或 {@code obs}；联调时用于确认当前走的哪条链路 */
    private String storage;

    /**
     * 由存储结果构造返回值。
     *
     * @param stored  存储结果
     * @param bizType 业务类型（当前不参与出参，保留为参数以便将来按类型附加策略）
     * @return 视图对象
     */
    public static UploadResultVO from(StoredFile stored, FileBizType bizType) {
        UploadResultVO vo = new UploadResultVO();
        vo.setUrl(stored.url());
        vo.setObjectKey(stored.objectKey());
        vo.setSize(stored.size());
        vo.setStorage(stored.storage());
        return vo;
    }
}
