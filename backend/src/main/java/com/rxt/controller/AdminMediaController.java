package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.service.MediaService;
import com.rxt.vo.MediaItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片资源管理接口（后台）。基础路径 /api/admin/media。
 *
 * <p>对应《软件需求规格说明书》「平台管理员 → 图片资源管理」。</p>
 *
 * <p><b>为什么删除用 query 参数而不是 {@code /admin/media/{objectKey}}</b>：
 * 对象键里含 {@code /}（形如 {@code product/2026/10/x.png}），放进路径变量会被
 * Spring 当成多段路径，要么匹配不上、要么得开 {@code /**} 通配 ——
 * 后者会把整个前缀变成贪婪匹配，以后想加子路径都没余地。用 query 参数则
 * 天然按「一个字符串」处理，不需要任何转义约定。</p>
 *
 * <p>角色校验（仅管理员）在 Service 层由 {@code PermissionChecker} 完成，
 * 与 {@link AdminUserController} 同一口径。</p>
 */
@RestController
@RequestMapping("/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaService mediaService;

    /**
     * 图片列表。带引用状态 —— 前端据此决定「删除」按钮是否可点。
     *
     * @param prefix   对象键前缀筛选，如 {@code product/}；不传表示全部
     * @param pageNum  当前页
     * @param pageSize 每页条数
     */
    @GetMapping
    public Result<IPage<MediaItemVO>> pageMedia(@RequestParam(required = false) String prefix,
                                                @RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "12") long pageSize) {
        return Result.success(mediaService.pageMedia(prefix, pageNum, pageSize));
    }

    /**
     * 删除图片对象。被业务数据引用时返回 400 并列出引用方，不做静默删除。
     */
    @DeleteMapping
    public Result<Void> deleteMedia(@RequestParam String objectKey) {
        mediaService.deleteMedia(objectKey);
        return Result.success();
    }
}
