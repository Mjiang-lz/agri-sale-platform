package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.AdminExpertUpdateDTO;
import com.rxt.service.ExpertService;
import com.rxt.vo.ExpertProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专家资源管理接口（后台）。基础路径 /api/admin/experts。
 *
 * <p>对应《软件需求规格说明书》「平台管理员 → 专家资源管理」职责，与业务端的
 * {@link ExpertController}（/api/expert，专家维护自己那一份）区分：
 * 本接口面向管理员跨专家操作，能看全量档案、能改审核状态。</p>
 *
 * <p>角色校验（仅管理员）与 {@link AdminUserController} 同一口径，放在 Service 层由
 * {@code PermissionChecker} 完成，控制器不再重复声明 —— 这样"谁能调"这件事
 * 只有一处真源，不依赖调用方是否记得走控制器。</p>
 *
 * <p>操作人身份一律取自登录态 {@code UserContext}，不接受客户端传入。</p>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminExpertController {

    private final ExpertService expertService;

    /**
     * 专家档案列表（专家资源管理首屏）。
     *
     * <p>与 {@code GET /api/expert/experts} 的关键差别：那个只返回<b>已通过审核</b>的
     * 专家（公开页用），本接口返回<b>全部</b>档案并带 {@code auditStatus}，
     * 否则管理员没法发现待审核的入驻申请。</p>
     *
     * <p>不做分页：入驻专家量级很小（种子数据里只有 2 位）。等真到几十位再改成分页，
     * 字段契约不变。</p>
     *
     * @param auditStatus 审核状态筛选，可选；不传表示不限
     * @param domain      专业领域筛选，可选；不传表示不限
     */
    @GetMapping("/experts")
    public Result<List<ExpertProfileVO>> listExperts(
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String domain) {
        return Result.success(expertService.listProfilesForAdmin(auditStatus, domain));
    }

    /**
     * 编辑专家档案（擅长领域、简介、审核状态）。
     *
     * <p>三个字段全空时返回 400「请至少填写一项要修改的内容」，而不是 200
     * 假成功 —— 管理页上"保存了但没变化"比报错更难排查。</p>
     *
     * <p><b>本接口不写 t_audit 审核留痕。</b>资料维护与审核动作是两件事：
     * 从这里改的审核状态不会计入 {@code GET /api/audit/statistics} 的审核工作量。
     * 注意这不是"忘了走正路"——{@code POST /api/audit/content/EXPERT/{expertId}}
     * <b>当前并不存在</b>：内容审核模块刻意只管辖 PRODUCT / KNOWLEDGE / COMMENT，
     * 传 EXPERT 会被类型白名单以 400 拒掉。理由见
     * {@code ExpertService#updateProfileForAdmin}。</p>
     */
    @PutMapping("/experts/{expertId}")
    public Result<Void> updateExpert(@PathVariable Long expertId,
                                     @RequestBody @Valid AdminExpertUpdateDTO dto) {
        expertService.updateProfileForAdmin(expertId, dto);
        return Result.success();
    }
}
