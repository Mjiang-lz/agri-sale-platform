package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.security.RoleConstant;
import com.rxt.service.ExpertService;
import com.rxt.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.rxt.security.PermissionChecker;
import com.rxt.vo.AppointmentVO;
import com.rxt.vo.ExpertDetailVO;
import com.rxt.vo.ExpertProfileVO;
import com.rxt.vo.ExpertVO;
import jakarta.validation.Valid;
import com.rxt.dto.AppointmentDTO;
import com.rxt.dto.AppointmentHandleDTO;
import com.rxt.dto.ExpertProfileUpdateDTO;
import com.rxt.dto.ExpertCertifyDTO;

@RestController
@RequestMapping("/expert")
public class ExpertController {

    @Autowired
    private ExpertService expertService;

    @Autowired
    private PermissionChecker permissionChecker;

    @PostMapping("/appointment/submit")
    public Result<Long> submit(@RequestBody @Valid AppointmentDTO dto) {
        permissionChecker.requireRole(RoleConstant.FARMER);
        Long farmerId = UserContext.currentUserId();
        Long apptId = expertService.makeAppointment(farmerId, dto);
        return Result.success(apptId);
    }

    /**
     * 我的预约列表。
     *
     * <p>查询范围由登录态决定，<b>不接受前端传 farmerId</b> —— 一旦接受，
     * 任意登录农户都能改个 id 翻走别人的预约记录（越权）。</p>
     *
     * <p>不做分页：单个农户的预约数量级很小（种子数据里最多 2 条），
     * 分页会给前端多一层解包负担而换不来任何收益。等真有农户攒到几十条
     * 再改成分页也不影响字段契约。</p>
     */
    @GetMapping("/appointment/my")
    public Result<List<AppointmentVO>> myAppointments() {
        permissionChecker.requireRole(RoleConstant.FARMER);
        return Result.success(expertService.myAppointments(UserContext.currentUserId()));
    }

    /**
     * 专家侧预约列表，只返回当前登录专家自己的预约。
     */
    @GetMapping("/appointment/expert")
    public Result<List<AppointmentVO>> expertAppointments(@RequestParam(required = false) String status) {
        permissionChecker.requireRole(RoleConstant.EXPERT);
        return Result.success(expertService.expertAppointments(UserContext.currentUserId(), status));
    }

    /**
     * 专家接受或拒绝预约。预约状态与备注真实写回 t_appointment。
     */
    @PostMapping("/appointment/{apptId}/handle")
    public Result<Void> handleAppointment(@PathVariable Long apptId,
                                          @RequestBody @Valid AppointmentHandleDTO dto) {
        permissionChecker.requireRole(RoleConstant.EXPERT);
        expertService.handleAppointment(UserContext.currentUserId(), apptId, dto);
        return Result.success();
    }

    /**
     * 专家一览（概设 UI-EXP-03「专家介绍」）。
     *
     * <p><b>刻意不加 {@code requireRole}</b>：浏览专家应该是所有登录用户的共性动作 ——
     * 采购方、银行用户同样需要看得到专家，只放给农户会与 UI-EXP-03 冲突。而
     * {@code /expert/**} 并不在 {@code PublicEndpoints} 白名单里，所以「必须登录」
     * 这层约束已经由既有安全配置兜住了，无需在此重复声明。</p>
     *
     * <p>不做分页：入驻专家量级很小（种子数据里只有 2 位），分页只会给前端多一层
     * 解包负担。等真到几十位再改成分页，字段契约不变。</p>
     *
     * @param domain 专业领域筛选，可选；不传表示不限领域
     */
    @GetMapping("/experts")
    public Result<List<ExpertVO>> experts(@RequestParam(required = false) String domain) {
        return Result.success(expertService.listExperts(domain));
    }

    /**
     * 专家详细介绍页（需求 9：每个专家要有一个单独的详细介绍页）。
     *
     * <p>列表页那张卡只够写一句履历，用户挑专家是照着自己的难题挑的，
     * 看完卡片仍不知道这位能不能解决自己的问题。详情页把擅长领域、
     * 答复过的问题、高频预约主题摊开，才是「对号入座」的依据。</p>
     *
     * <p>未过审的专家与不存在的专家返回同一个「不存在」，理由见
     * {@code ExpertServiceImpl#getExpertDetail}。</p>
     */
    @GetMapping("/experts/{expertId}")
    public Result<ExpertDetailVO> expertDetail(@PathVariable Long expertId) {
        return Result.success(expertService.getExpertDetail(expertId));
    }

    /**
     * 提交专家资格认证材料（需求 10）。
     *
     * <p>与 {@link #profile()} 的区别：那个用于「已通过的专家维护自己那一份」，
     * 本接口用于「刚注册、还没通过认证」的人首次提交资质。
     * 二者的关键差别是<b>能不能改审核状态</b>：本接口只写材料，
     * {@code audit_status} 一律由管理员侧接口决定。</p>
     *
     * <p>需要专家角色 —— 非专家账号进来直接 403，这是「专家才能申请成为专家」
     * 的第一道闸；真正的准入判断在 Service 层（是否已有档案/是否已通过）。</p>
     */
    @PostMapping("/certify")
    public Result<Void> certify(@RequestBody @Valid ExpertCertifyDTO dto) {
        permissionChecker.requireRole(RoleConstant.EXPERT);
        expertService.certify(UserContext.currentUserId(), dto);
        return Result.success();
    }

    /**
     * 专家查看本人档案（《软件需求规格说明书》「个人信息 → 专家信息管理」）。
     *
     * <p>档案由登录态定位，<b>不接受 {@code expertId} 入参</b> —— 一旦接受，
     * 任意专家都能按 id 翻别人的档案。</p>
     */
    @GetMapping("/profile")
    public Result<ExpertProfileVO> profile() {
        permissionChecker.requireRole(RoleConstant.EXPERT);
        return Result.success(expertService.getProfile(UserContext.currentUserId()));
    }

    /**
     * 专家编辑本人档案（擅长领域、简介与服务范围）。
     *
     * <p>入参 {@link ExpertProfileUpdateDTO} 里<b>没有</b> {@code auditStatus} /
     * {@code workloadStat} / {@code userId}，所以"专家不能改自己的审核状态"是
     * <b>编译期</b>保证，而不是靠运行时记得过滤某个字段。</p>
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid ExpertProfileUpdateDTO dto) {
        permissionChecker.requireRole(RoleConstant.EXPERT);
        expertService.updateProfile(UserContext.currentUserId(), dto);
        return Result.success();
    }
}