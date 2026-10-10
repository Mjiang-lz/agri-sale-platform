package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.LoanGroupDTO;
import com.rxt.security.UserContext;
import com.rxt.service.LoanGroupService;
import com.rxt.vo.LoanGroupCreateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联合贷款小组接口。基础路径 /api/finance/group。
 *
 * <p>三个动作都写操作，一律要求登录并校验农户身份（角色校验在
 * {@code LoanGroupServiceImpl} 里统一做）。</p>
 *
 * <p><b>路径注意</b>：{@code server.servlet.context-path} 已经是 {@code /api}，
 * 这里的 {@code @RequestMapping} 只写 {@code /finance}。若再写成 {@code /api/finance}，
 * 真实路径会变成 {@code /api/api/finance/group}，前端按 {@code /api/finance/group}
 * 调用时全部 404 —— 而且因为 fallback 到了「接口不存在」的兜底分支，
 * 排查时容易误以为是服务没起来。</p>
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class LoanGroupController {

    private final LoanGroupService loanGroupService;

    /**
     * 组建联合贷款小组。
     *
     * <p>返回入组明细而不是空结果：被推荐人可能因为「已在其他进行中小组」
     * 或「尚未提交同产品申请」而进不来，这些必须如实回传，否则发起人会以为
     * 小组已经凑齐 —— 与「提示成功、库里没数据」是同一类故障。</p>
     */
    @PostMapping("/group")
    public Result<LoanGroupCreateVO> createGroup(@RequestBody LoanGroupDTO dto) {
        return Result.success(loanGroupService.createGroup(UserContext.currentUserId(), dto));
    }

    /**
     * 加入指定小组。
     */
    @PutMapping("/group/{id}/join")
    public Result<Void> joinGroup(@PathVariable Long id) {
        loanGroupService.joinGroup(id, UserContext.currentUserId());
        return Result.success();
    }

    /**
     * 退出指定小组。
     */
    @DeleteMapping("/group/{id}/quit")
    public Result<Void> quitGroup(@PathVariable Long id) {
        loanGroupService.quitGroup(id, UserContext.currentUserId());
        return Result.success();
    }
}
