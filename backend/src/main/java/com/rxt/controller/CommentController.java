package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.dto.CommentPublishDTO;
import com.rxt.security.UserContext;
import com.rxt.service.CommentService;
import com.rxt.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评论接口。基础路径 /api/comment。
 *
 * <p>发表评论属于「写我的数据」，评论人身份一律取自 {@link UserContext}（登录态），
 * 不接受客户端传入 userId。查询接口只返回已过审评论，因此不暴露审核状态过滤参数 ——
 * 审核中的内容走管理端审核接口查看，不在买家端开放。</p>
 *
 * <p>本控制器**未加入 WebMvcConfig 白名单**，两个接口都要求登录。
 * 这与 frontend-web 现有行为一致（全局路由守卫除登录/注册外均要求 token）；
 * 若将来产品要求游客可看评论，应先统一决定是否放行 /trade/products/** 整条商品链路，
 * 而不是单独把评论接口开个口子。</p>
 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表商品评论。
     *
     * <p>新评论一律为「待审核」，需管理员审核通过后才会出现在商品评论列表中；
     * 返回值里带着初始状态，前端据此提示用户「评论已提交，审核通过后展示」。</p>
     */
    @PostMapping("/product")
    public Result<CommentVO> publishProductComment(@RequestBody @Valid CommentPublishDTO dto) {
        return Result.success(commentService.publishProductComment(UserContext.currentUserId(), dto));
    }

    /**
     * 分页查询某商品下已过审的评论，按评论时间倒序。
     */
    @GetMapping("/product/list")
    public Result<IPage<CommentVO>> pageProductComments(@RequestParam Long productId,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(commentService.pageProductComments(productId, pageNum, pageSize));
    }
}
