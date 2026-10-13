package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.security.UserContext;
import com.rxt.service.NotificationService;
import com.rxt.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（通知中心）。基础路径 /api/notification。
 *
 * <p>四个路径<b>都不进白名单</b>：通知里含订单号、商品名、提问摘要，
 * 放行给游客等于把用户私有内容挂在公网上。</p>
 *
 * <p>身份一律从 {@link UserContext} 取当前登录用户，接口上<b>没有「查谁的通知」这类入参</b>，
 * 客户端无法通过改参数越权读别人或替别人标记已读 —— 越权由 service 层按归属校验挡下（404）。</p>
 *
 * <p><b>刻意没有「发通知」的接口。</b>通知只能由服务端在业务事件里调用
 * {@code NotificationService.send(...)} 写入。开放给客户端就意味着任何人可以给任何人推消息，
 * 这是通知类功能的头号事故来源。</p>
 */
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 我的通知列表。按产生时间倒序，同一时间再按主键倒序（保证翻页顺序稳定）。
     *
     * @param isRead 0 只看未读、1 只看已读、不传 = 全部；其它值 400
     */
    @GetMapping("/list")
    public Result<IPage<NotificationVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Integer isRead) {
        return Result.success(
                notificationService.pageMine(UserContext.currentUserId(), pageNum, pageSize, isRead));
    }

    /**
     * 我的未读数。顶栏红点用 —— 它每次进任意页面都要取，而列表只在打开通知中心时才需要，
     * 所以单独一个接口（复用列表接口会让每次翻页都拉一遍全量通知）。
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.countUnread(UserContext.currentUserId()));
    }

    /**
     * 标记单条已读。幂等：重复标记仍成功，且不刷新 read_time。
     *
     * <p>路径是<b>两段</b>（{@code {notificationId}/read}），与下面的 {@code /read-all}（一段）
     * 天然不冲突 —— 不存在 {@code read-all} 被当成 id 解析的问题。
     * 用 PUT 而非 POST：与既有 {@code PUT /user/addresses/{addressId}/default} 一致，
     * 「把某个标识位的状态改成已读」在本项目里就是 PUT。</p>
     */
    @PutMapping("/{notificationId}/read")
    public Result<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(UserContext.currentUserId(), notificationId);
        return Result.success();
    }

    /**
     * 全部标记已读。只影响本人未读的通知；本人没有未读时也是成功（幂等）。
     */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(UserContext.currentUserId());
        return Result.success();
    }
}
