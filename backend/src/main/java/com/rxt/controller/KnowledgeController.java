package com.rxt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.Result;
import com.rxt.dto.KnowledgeCreateDTO;
import com.rxt.entity.Knowledge;
import com.rxt.security.UserContext;
import com.rxt.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    // 列表接口，支持分类筛选 + 关键词搜索 + 分页
    @GetMapping("/list")
    public Result<Page<Knowledge>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(knowledgeService.pageQuery(category, keyword, page, size));
    }

    /** 当前专家自己的知识投稿，含待审核、已通过、未通过全部状态。 */
    @GetMapping("/my")
    public Result<List<Knowledge>> myKnowledge() {
        return Result.success(knowledgeService.myKnowledge(UserContext.currentUserId()));
    }

    // 详情接口
    @GetMapping("/{id}")
    public Result<Knowledge> detail(@PathVariable Long id) {
        return Result.success(knowledgeService.getDetail(id));
    }

    /**
     * 专家新增农业知识。
     * 新内容写入 t_knowledge，审核状态固定为“待审核”，通过管理端知识审核后才会公开。
     */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid KnowledgeCreateDTO dto) {
        return Result.success(knowledgeService.createKnowledge(UserContext.currentUserId(), dto));
    }

    /**
     * 专家编辑自己的农业知识。
     *
     * <p>对应《软件需求规格说明书》「个人信息 → 专家管理」的处理项<b>「新增与编辑知识」</b>。
     * 归属由服务端按登录态校验（只能改自己的），且**编辑后审核状态一律回到「待审核」** ——
     * 已通过的内容改了正文必须重新过审，否则等于开了一条绕过审核的改稿通道。</p>
     *
     * <p>入参复用 {@link KnowledgeCreateDTO}，字段约束与新增完全一致。</p>
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid KnowledgeCreateDTO dto) {
        knowledgeService.updateKnowledge(UserContext.currentUserId(), id, dto);
        return Result.success();
    }

    /**
     * 专家删除自己的农业知识投稿（「我的投稿」列表里的删除）。
     *
     * <p>归属由服务端按登录态校验（只能删自己的）。删他人返回 403，不存在返回 404 ——
     * 两个码刻意分开，理由与编辑接口一致。</p>
     *
     * <p>与商品的删除不同，这里**不做软删**：全库没有外键指向 {@code t_knowledge}，
     * 知识也不参与下单链路，删行不留悬空引用，所以不需要 SRS 那条「被订单引用则改判下架」
     * 的例外。审核流水会保留（审核确实发生过），详见
     * {@link com.rxt.service.KnowledgeService#deleteKnowledge}。</p>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledge(UserContext.currentUserId(), id);
        return Result.success();
    }
}