package com.rxt.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 图片管理页的一条记录（后台）。
 *
 * <p>{@code referenced} 与 {@code references} 是这一页存在的理由：
 * 只把对象列出来，管理员仍然不知道<b>哪一个能删</b>。列表接口在返回前会扫一遍
 * {@code t_user.avatar_url} / {@code t_product.image_url} / {@code t_product.images} /
 * {@code t_knowledge.image_url}，把"有没有人用"直接标出来。</p>
 */
@Data
public class MediaItemVO {

    /** 对象键，形如 {@code product/2026/10/<uuid>.png}。删除接口的入参 */
    private String objectKey;

    /** 对外可访问地址：本地模式相对路径、OBS 模式绝对地址 */
    private String url;

    /** 字节数 */
    private Long size;

    /** 最后修改时间，取不到时为 null */
    private LocalDateTime lastModified;

    /** 是否被业务数据引用。<b>true 时删除接口一定会拒绝</b> */
    private Boolean referenced;

    /**
     * 引用方描述，如 {@code 商品 #24「赣南脐橙」主图}。
     *
     * <p>未引用时是空数组而不是 null —— 前端不必再判空。</p>
     */
    private List<String> references;
}
