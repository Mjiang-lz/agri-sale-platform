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
import jakarta.validation.Valid;
import com.rxt.dto.AppointmentDTO;

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
}