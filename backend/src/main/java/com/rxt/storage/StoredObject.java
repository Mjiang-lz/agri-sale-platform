package com.rxt.storage;

import java.time.LocalDateTime;

/**
 * 存储中的一个对象（列表用）。与 {@link StoredFile} 的区别：
 * 后者是「我刚刚存进去的东西」的返回值，前者是「存储里现在已经有的东西」的一条记录。
 *
 * <p>字段刻意与 {@link StoredFile} 保持同形（url / objectKey / size），
 * 前端两个场景可以复用同一套渲染逻辑。</p>
 *
 * @param objectKey    对象键（存储侧相对路径），如 {@code product/2026/10/&lt;uuid&gt;.png}
 * @param url          对外可访问地址：本地模式 {@code /api/files/<objectKey>}，
 *                     OBS 模式 {@code https://<bucket>.<endpoint>/<objectKey>}
 * @param size         字节数；取不到时为 0
 * @param lastModified 最后修改时间；取不到时为 {@code null}
 */
public record StoredObject(String objectKey, String url, long size, LocalDateTime lastModified) {
}
