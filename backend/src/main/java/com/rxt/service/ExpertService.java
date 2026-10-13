package com.rxt.service;

import com.rxt.dto.AdminExpertUpdateDTO;
import com.rxt.dto.AppointmentDTO;
import com.rxt.dto.AppointmentHandleDTO;
import com.rxt.dto.ExpertProfileUpdateDTO;
import com.rxt.vo.AppointmentVO;
import com.rxt.vo.ExpertDetailVO;
import com.rxt.vo.ExpertProfileVO;
import com.rxt.vo.ExpertVO;

import java.util.List;

/**
 * 专家助力模块业务接口。
 *
 * <p>实现类位于 com.rxt.service.impl，业务规则见《软件需求规格说明书》对应功能点。</p>
 */
public interface ExpertService {

    /**
     * 预约专家。
     */
    Long makeAppointment(Long farmerId, AppointmentDTO dto);

    /**
     * 我的预约列表（REQ-11「预约状态与结果通知」的查询侧）。
     *
     * @param farmerId 农户用户主键，取自登录态；不接受按他人 id 查询
     * @return 该农户的全部预约，按提交时间倒序
     */
    List<AppointmentVO> myAppointments(Long farmerId);

    /** 专家侧预约列表，按当前登录专家过滤。 */
    List<AppointmentVO> expertAppointments(Long userId, String status);

    /** 专家接受或拒绝预约。 */
    void handleAppointment(Long userId, Long apptId, AppointmentHandleDTO dto);

    /**
     * 专家一览（概设 UI-EXP-03「专家介绍」）。
     *
     * @param domain 专业领域筛选；null 或空串表示不限
     * @return 已通过审核的入驻专家，按专家主键升序；无数据时返回空列表（不是 null）
     */
    List<ExpertVO> listExperts(String domain);

    /**
     * 专家查看本人的档案。
     *
     * <p>对应《软件需求规格说明书》「个人信息 → 专家信息管理」。
     * 查询范围由登录态决定，不接受按他人 id 查询。</p>
     *
     * @param userId 当前登录用户主键
     * @return 档案视图，含 {@code auditStatus} 供本人了解过审情况
     */
    /**
     * 专家详细介绍：基础资料 + 实时统计的服务业绩 + 答复过的提问摘要 + 高频预约主题。
     *
     * <p>统计项一律现算，不读 {@code workload_stat}（那是手写文案，与真实条数对不上）。
     * 未通过资格认证的专家与不存在的专家返回同一个「不存在」。</p>
     *
     * @param expertId 专家主键
     */
    ExpertDetailVO getExpertDetail(Long expertId);

    ExpertProfileVO getProfile(Long userId);

    /**
     * 专家编辑本人的档案（擅长领域、简介与服务范围）。
     *
     * <p>{@code workloadStat} / {@code auditStatus} / {@code userId} 不在 DTO 里，
     * 因此传了也不生效 —— 审核状态只能由管理员改，工作量统计由系统算。</p>
     *
     * @param userId 当前登录用户主键
     * @param dto    待更新字段；两个字段都为空时抛 400（避免"提交成功但什么都没改"）
     */
    void updateProfile(Long userId, ExpertProfileUpdateDTO dto);

    /**
     * 管理员：专家资源列表（SRS 管理员职责「专家资源管理」）。
     *
     * @param auditStatus 审核状态筛选；null 或空串表示不限
     * @param domain      专业领域筛选；null 或空串表示不限
     * @return 全部专家的档案，按创建时间、专家主键升序（先到先审，与审核队列口径一致）
     */
    List<ExpertProfileVO> listProfilesForAdmin(String auditStatus, String domain);

    /**
     * 管理员：编辑专家档案。
     *
     * <p>与专家本人编辑的差别只有一处 —— 这里可以改 {@code auditStatus}，
     * 因为审核是管理员职责。但<b>资料维护不等于审核动作</b>：本方法不写
     * {@code t_audit} 审核留痕，所以从这里改的审核状态不会计入
     * {@code GET /api/audit/statistics} 的审核工作量。</p>
     *
     * <p><b>为什么不留痕还得走这里</b>：内容审核模块（{@code AuditServiceImpl}）
     * 刻意只管辖 PRODUCT / KNOWLEDGE / COMMENT 三类内容，传 EXPERT 会被
     * {@code requireValidTargetType} 以 400「内容类型不合法」拒掉 ——
     * 因为 t_audit 里的 EXPERT 流水属 M1 主体审核，混进内容审核列表会是一批
     * 没有标题的记录。也就是说 {@code POST /api/audit/content/EXPERT/{id}}
     * <b>当前并不存在</b>，本方法是今天唯一能改专家审核状态的路径。
     * 后续若要补留痕，应该新开主体审核接口，而不是放宽内容审核的类型白名单。</p>
     *
     * @param expertId 专家主键
     * @param dto      待更新字段，比专家侧多一个 {@code auditStatus}
     */
    void updateProfileForAdmin(Long expertId, AdminExpertUpdateDTO dto);
}
