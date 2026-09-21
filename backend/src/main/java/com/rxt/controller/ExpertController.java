package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.entity.Knowledge;
import com.rxt.service.ExpertService;
import com.rxt.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.rxt.security.PermissionChecker;
import jakarta.validation.Valid;
import com.rxt.dto.AppointmentDTO;

@RestController
@RequestMapping("/expert")
public class ExpertController {

    @Autowired
    private ExpertService expertService;

    @Autowired
    private PermissionChecker permissionChecker;

    @GetMapping("/knowledge")
    public Result<List<Knowledge>> listKnowledge(@RequestParam(required = false) String category) {
        return Result.success(expertService.listKnowledge(category));
    }

    @PostMapping("/appointment/submit")
    public Result<Long> submit(@RequestBody @Valid AppointmentDTO dto) {
        permissionChecker.requireRole("农户");
        Long farmerId = UserContext.currentUserId();
        Long apptId = expertService.makeAppointment(farmerId, dto);
        return Result.success(apptId);
    }
}