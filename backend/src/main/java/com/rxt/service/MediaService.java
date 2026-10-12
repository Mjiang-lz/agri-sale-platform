package com.rxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.vo.MediaItemVO;

/**
 * 图片资源管理业务接口（后台）。
 *
 * <p>对应《软件需求规格说明书》「平台管理员 → 图片资源管理」。
 * 与 {@link com.rxt.storage.FileStorageService} 的分工：
 * 存储层只管「列出来 / 删掉」，「这个对象还有没有业务行在用」是业务知识，
 * 落在本接口。</p>
 */
public interface MediaService {

    /**
     * 图片列表（含引用状态）。
     *
     * <p>列表内容来自当前启用的存储实现（本地磁盘或 OBS），
     * 引用状态来自数据库 —— 两边合起来才够管理员判断「这条能不能删」。</p>
     *
     * @param prefix   对象键前缀筛选，如 {@code product/}；null 或空串表示不限
     * @param pageNum  当前页，从 1 开始
     * @param pageSize 每页条数
     */
    IPage<MediaItemVO> pageMedia(String prefix, long pageNum, long pageSize);

    /**
     * 删除一个图片对象。
     *
     * <p><b>被业务数据引用时一律拒绝</b>，并回报引用方。
     * 删掉一张正在用的图，页面上就是一块裂图 —— 而且因为数据库里的 URL 还在，
     * 排查时完全看不出「曾经有过图」，只会以为前端没渲染。</p>
     *
     * @param objectKey 对象键
     */
    void deleteMedia(String objectKey);
}
