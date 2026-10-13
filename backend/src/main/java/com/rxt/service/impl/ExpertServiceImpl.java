package com.rxt.service.impl;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.AdminExpertUpdateDTO;
import com.rxt.dto.AppointmentDTO;
import com.rxt.dto.AppointmentHandleDTO;
import com.rxt.dto.ExpertProfileUpdateDTO;
import com.rxt.entity.Appointment;
import com.rxt.entity.ExpertInfo;
import com.rxt.entity.Question;
import com.rxt.mapper.AppointmentMapper;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.QuestionMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.entity.User;
import com.rxt.security.PermissionChecker;
import com.rxt.service.ExpertService;
import com.rxt.vo.AppointmentVO;
import com.rxt.vo.ExpertDetailVO;
import com.rxt.vo.ExpertProfileVO;
import com.rxt.vo.ExpertVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpertServiceImpl implements ExpertService {

    /** 审核结论词表里的「通过」。全仓没有共享常量类，各服务各自持有（同 HomeServiceImpl:49）。 */
    private static final String AUDIT_APPROVED = "已通过";

    /** 提问状态：已回复（专家详情页的「答复过的问题」只认这个状态） */
    private static final String QUESTION_ANSWERED = "已回复";

    /** 预约状态：已接受 / 已完成（两者都算服务真正闭环了） */
    private static final String APPT_ACCEPTED = "已接受";
    private static final String APPT_FINISHED = "已完成";

    @Autowired private KnowledgeMapper knowledgeMapper;
    @Autowired private QuestionMapper questionMapper;
    @Autowired private AppointmentMapper appointmentMapper;
    @Autowired private ExpertInfoMapper expertInfoMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PermissionChecker permissionChecker;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long makeAppointment(Long farmerId, AppointmentDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectById(dto.getExpertId());
        if (expert == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "专家不存在");
        }

        Appointment app = new Appointment();
        app.setFarmerId(farmerId);
        app.setExpertId(dto.getExpertId());
        app.setApptTime(dto.getApptTime());
        app.setApptTopic(dto.getApptTopic());
        app.setRemark(dto.getRemark());
        app.setApptStatus("待处理");
        appointmentMapper.insert(app);
        return app.getApptId();
    }

    /**
     * 我的预约列表。
     *
     * <p>身份只认登录态传入的 farmerId，<b>不提供按他人 id 查询的重载</b> ——
     * 这类「我的某物」接口一旦接受调用方指定归属人，任意登录用户都能拿别人的
     * id 翻走他人预约（越权）。</p>
     *
     * <p>专家姓名与领域按 {@code IN} 批量装配，不做逐行查库（N+1）。
     * 姓名在 {@code t_user}、领域在 {@code t_expert}，所以要两次批量查询。</p>
     */
    @Override
    public List<AppointmentVO> myAppointments(Long farmerId) {
        List<Appointment> appointments = appointmentMapper.selectList(
                Wrappers.<Appointment>lambdaQuery()
                        .eq(Appointment::getFarmerId, farmerId)
                        .orderByDesc(Appointment::getApptId));
        if (appointments.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> expertIds = appointments.stream()
                .map(Appointment::getExpertId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, ExpertInfo> experts = expertIds.isEmpty() ? Collections.emptyMap()
                : expertInfoMapper.selectBatchIds(expertIds).stream()
                        .collect(Collectors.toMap(ExpertInfo::getExpertId, Function.identity(), (a, b) -> a));

        Set<Long> expertUserIds = experts.values().stream()
                .map(ExpertInfo::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, String> expertNames = expertUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(expertUserIds).stream()
                        .collect(Collectors.toMap(User::getUserId, User::getRealName, (a, b) -> a));

        List<AppointmentVO> result = new ArrayList<>(appointments.size());
        for (Appointment appointment : appointments) {
            AppointmentVO vo = new AppointmentVO();
            vo.setApptId(appointment.getApptId());
            vo.setExpertId(appointment.getExpertId());
            vo.setApptTime(appointment.getApptTime());
            vo.setApptTopic(appointment.getApptTopic());
            vo.setApptStatus(appointment.getApptStatus());
            vo.setRemark(appointment.getRemark());
            vo.setCreateTime(appointment.getCreateTime());

            ExpertInfo expert = appointment.getExpertId() == null
                    ? null : experts.get(appointment.getExpertId());
            if (expert != null) {
                vo.setExpertDomain(expert.getExpertDomain());
                vo.setExpertName(expertNames.get(expert.getUserId()));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 专家侧预约列表。
     *
     * <p>专家身份由登录用户映射到 t_expert，前端不能传 expertId。
     * 状态为空时返回全部预约，否则按预约状态过滤。</p>
     */
    @Override
    public List<AppointmentVO> expertAppointments(Long userId, String status) {
        ExpertInfo expert = expertInfoMapper.selectOne(Wrappers.<ExpertInfo>lambdaQuery()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可查看预约工作台");
        }

        List<Appointment> appointments = appointmentMapper.selectList(
                Wrappers.<Appointment>lambdaQuery()
                        .eq(Appointment::getExpertId, expert.getExpertId())
                        .eq(StringUtils.hasText(status), Appointment::getApptStatus, status)
                        .orderByDesc(Appointment::getApptId));
        if (appointments.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> farmerIds = appointments.stream()
                .map(Appointment::getFarmerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, User> farmers = farmerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(farmerIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));

        User expertUser = userMapper.selectById(expert.getUserId());
        String expertName = expertUser == null ? "" : expertUser.getRealName();

        List<AppointmentVO> result = new ArrayList<>(appointments.size());
        for (Appointment appointment : appointments) {
            AppointmentVO vo = new AppointmentVO();
            vo.setApptId(appointment.getApptId());
            vo.setExpertId(appointment.getExpertId());
            vo.setExpertName(expertName);
            vo.setExpertDomain(expert.getExpertDomain());
            vo.setFarmerId(appointment.getFarmerId());
            User farmer = appointment.getFarmerId() == null ? null : farmers.get(appointment.getFarmerId());
            vo.setFarmerName(farmer == null ? "" : farmer.getRealName());
            vo.setApptTime(appointment.getApptTime());
            vo.setApptTopic(appointment.getApptTopic());
            vo.setApptStatus(appointment.getApptStatus());
            vo.setRemark(appointment.getRemark());
            vo.setCreateTime(appointment.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 专家接受或拒绝预约。
     *
     * <p>仅允许处理“待处理”预约；接受时可省略备注，拒绝时必须说明原因。
     * 同时校验预约归属，防止专家处理其他专家的预约。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppointment(Long userId, Long apptId, AppointmentHandleDTO dto) {
        ExpertInfo expert = expertInfoMapper.selectOne(Wrappers.<ExpertInfo>lambdaQuery()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可处理预约");
        }

        Appointment appointment = appointmentMapper.selectById(apptId);
        if (appointment == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "预约不存在");
        }
        if (!expert.getExpertId().equals(appointment.getExpertId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "不能处理其他专家的预约");
        }
        if (!"待处理".equals(appointment.getApptStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该预约已处理，请刷新后查看");
        }
        if ("已拒绝".equals(dto.getApptStatus()) && !StringUtils.hasText(dto.getRemark())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "拒绝预约时必须填写原因");
        }

        appointment.setApptStatus(dto.getApptStatus());
        appointment.setRemark(StringUtils.hasText(dto.getRemark())
                ? dto.getRemark().trim()
                : "专家已接受预约，请按约定时间联系。");
        appointmentMapper.updateById(appointment);
    }

    /**
     * 专家一览（概设 UI-EXP-03「专家介绍」）。
     *
     * <p>只返回已通过审核的入驻专家 —— 未过审的专家不该出现在公开页面上。
     * 领域筛选是可选的：{@code domain} 为 null 或空白时不加该条件。</p>
     *
     * <p>姓名与头像在 {@code t_user}、其余字段在 {@code t_expert}，因此按 {@code IN}
     * 批量装配一次，而不是逐行查库（N+1）。原前端为此额外调了
     * {@code GET /user/{id}/brief}，那个端点并不存在，所以每次开首页都会报错 ——
     * 这里直接把 {@code realName} / {@code avatarUrl} 带出，调用方无需再补一轮请求。</p>
     *
     * <p>用 {@code realName} 兜底为空串而不是 null：模板里做的是
     * {@code {{ e.realName }}}，为 null 时 Vue 会渲染出空字符串，但若调用方要做
     * {@code .trim()} 之类的处理就会炸。统一给空串，契约更稳。</p>
     */
    @Override
    public List<ExpertVO> listExperts(String domain) {
        List<ExpertInfo> experts = expertInfoMapper.selectList(
                Wrappers.<ExpertInfo>lambdaQuery()
                        .eq(ExpertInfo::getAuditStatus, AUDIT_APPROVED)
                        .eq(StringUtils.hasText(domain), ExpertInfo::getExpertDomain, domain)
                        .orderByAsc(ExpertInfo::getExpertId));
        if (experts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, User> users = loadUsers(experts);

        List<ExpertVO> result = new ArrayList<>(experts.size());
        for (ExpertInfo expert : experts) {
            ExpertVO vo = new ExpertVO();
            vo.setExpertId(expert.getExpertId());
            vo.setUserId(expert.getUserId());
            vo.setExpertDomain(expert.getExpertDomain());
            vo.setIntroduction(expert.getIntroduction());
            vo.setWorkloadStat(expert.getWorkloadStat());

            User user = expert.getUserId() == null ? null : users.get(expert.getUserId());
            // 专家档案的 user_id 理论上总是能命中 t_user；真命不中也要保持
            // realName 非 null（见方法注释），故给空串而不是留 null。
            vo.setRealName(user == null || user.getRealName() == null ? "" : user.getRealName());
            vo.setAvatarUrl(user == null ? null : user.getAvatarUrl());
            result.add(vo);
        }
        return result;
    }

    @Override
    public ExpertDetailVO getExpertDetail(Long expertId) {
        ExpertInfo expert = expertInfoMapper.selectById(expertId);
        // 未过审的专家与不存在的专家返回同一个错：分开报错等于给未过审的人
        // 一个「专家存在、只是还没过审」的存在性探测接口。
        if (expert == null || !AUDIT_APPROVED.equals(expert.getAuditStatus())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "专家不存在或尚未通过资格认证");
        }

        User user = expert.getUserId() == null ? null : userMapper.selectById(expert.getUserId());

        ExpertDetailVO vo = new ExpertDetailVO();
        vo.setExpertId(expert.getExpertId());
        vo.setUserId(expert.getUserId());
        vo.setExpertDomain(expert.getExpertDomain());
        vo.setIntroduction(expert.getIntroduction());
        vo.setWorkloadStat(expert.getWorkloadStat());
        vo.setRealName(user == null || user.getRealName() == null ? "" : user.getRealName());
        vo.setAvatarUrl(user == null ? null : user.getAvatarUrl());

        // 下面四个统计全部现算，不读 workload_stat —— 那是手写文案，
        // 与真实条数对不上（库里写「已回复提问 42 条」，实际可能只有个位数）。
        // 详情页摆一个点进去对不上的数字，整页可信度就没了。
        vo.setAnsweredCount(countToInt(questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                .eq(Question::getExpertId, expertId)
                .eq(Question::getStatus, QUESTION_ANSWERED))));
        vo.setAskedCount(countToInt(questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                .eq(Question::getExpertId, expertId))));

        List<Appointment> appointments = appointmentMapper.selectList(
                Wrappers.<Appointment>lambdaQuery()
                        .eq(Appointment::getExpertId, expertId));
        vo.setAppointmentCount(appointments.size());
        vo.setFinishedCount((int) appointments.stream()
                .filter(a -> APPT_ACCEPTED.equals(a.getApptStatus()) || APPT_FINISHED.equals(a.getApptStatus()))
                .count());

        vo.setRecentAnswers(loadRecentAnswers(expertId));
        vo.setAppointmentTopics(extractTopics(appointments));
        return vo;
    }

    /** 该专家最近答复过的提问摘要（最多 5 条）。 */
    private List<ExpertDetailVO.AnsweredQuestion> loadRecentAnswers(Long expertId) {
        List<Question> questions = questionMapper.selectList(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getExpertId, expertId)
                        .eq(Question::getStatus, QUESTION_ANSWERED)
                        .orderByDesc(Question::getAskTime)
                        .orderByDesc(Question::getQuestionId)
                        .last("LIMIT 5"));
        List<ExpertDetailVO.AnsweredQuestion> result = new ArrayList<>(questions.size());
        for (Question q : questions) {
            ExpertDetailVO.AnsweredQuestion item = new ExpertDetailVO.AnsweredQuestion();
            item.setContent(truncate(q.getContent(), 60));
            item.setAskTime(q.getAskTime() == null ? null
                    : q.getAskTime().toLocalDate().toString());
            result.add(item);
        }
        return result;
    }

    /**
     * 从预约主题里提取高频关键词。
     *
     * <p>直接摆原始 {@code appt_topic} 没意义 —— 用户写的是「李子不熟怎么办」
     * 这种一句话，重复率低、互相比对不上。这里只取出现 2 次以上的主题，
     * 让「找他的人都在问什么」这件事真的看得出来；一个都没有就不返回该分区。</p>
     */
    private List<String> extractTopics(List<Appointment> appointments) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Appointment a : appointments) {
            String topic = a.getApptTopic();
            if (!StringUtils.hasText(topic)) {
                continue;
            }
            String key = truncate(topic.trim(), 16);
            counter.merge(key, 1, Integer::sum);
        }
        return counter.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 按字符截断并补省略号。中文按字算即可，这里不做词边界处理。 */
    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** selectCount 回 Long，VO 里声明的是 Integer；null 一律归 0，别把 null 抛给前端。 */
    private Integer countToInt(Long count) {
        return count == null ? 0 : count.intValue();
    }

    @Override
    public ExpertProfileVO getProfile(Long userId) {
        ExpertInfo expert = requireOwnExpert(userId);
        return toProfileVO(expert, userMapper.selectById(expert.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, ExpertProfileUpdateDTO dto) {
        ExpertInfo expert = requireOwnExpert(userId);
        requireSomethingToUpdate(dto.getExpertDomain(), dto.getIntroduction(), null);

        ExpertInfo update = new ExpertInfo();
        update.setExpertId(expert.getExpertId());
        // 用 hasText 而不是 != null 判领域：传个空串上来时 hasText 为 false 会跳过，
        // 而"非 null 就写"会把已有领域清空 —— 那不是"没改"，是"改坏了"。
        if (StringUtils.hasText(dto.getExpertDomain())) {
            update.setExpertDomain(dto.getExpertDomain().trim());
        }
        // 简介允许传空串（清空简介是合理诉求），所以这里只挡 null。
        if (dto.getIntroduction() != null) {
            update.setIntroduction(dto.getIntroduction().trim());
        }
        expertInfoMapper.updateById(update);
    }

    @Override
    public List<ExpertProfileVO> listProfilesForAdmin(String auditStatus, String domain) {
        permissionChecker.requireAdmin();
        List<ExpertInfo> experts = expertInfoMapper.selectList(
                Wrappers.<ExpertInfo>lambdaQuery()
                        .eq(StringUtils.hasText(auditStatus), ExpertInfo::getAuditStatus, auditStatus)
                        .eq(StringUtils.hasText(domain), ExpertInfo::getExpertDomain, domain)
                        .orderByAsc(ExpertInfo::getCreateTime)
                        .orderByAsc(ExpertInfo::getExpertId));
        if (experts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, User> users = loadUsers(experts);
        List<ExpertProfileVO> result = new ArrayList<>(experts.size());
        for (ExpertInfo expert : experts) {
            result.add(toProfileVO(expert, expert.getUserId() == null ? null : users.get(expert.getUserId())));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfileForAdmin(Long expertId, AdminExpertUpdateDTO dto) {
        permissionChecker.requireAdmin();
        ExpertInfo exist = expertInfoMapper.selectById(expertId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "专家档案不存在");
        }
        requireSomethingToUpdate(dto.getExpertDomain(), dto.getIntroduction(), dto.getAuditStatus());

        ExpertInfo update = new ExpertInfo();
        update.setExpertId(expertId);
        if (StringUtils.hasText(dto.getExpertDomain())) {
            update.setExpertDomain(dto.getExpertDomain().trim());
        }
        if (dto.getIntroduction() != null) {
            update.setIntroduction(dto.getIntroduction().trim());
        }
        // 审核状态只在管理员侧这条路径上可写（DTO 层已做白名单校验）。
        // 注意本方法刻意不写 t_audit 留痕，理由见 ExpertService#updateProfileForAdmin。
        if (StringUtils.hasText(dto.getAuditStatus())) {
            update.setAuditStatus(dto.getAuditStatus());
        }
        expertInfoMapper.updateById(update);
    }

    /** 取当前登录用户的专家档案；没有则说明不是专家（或尚无档案）。 */
    private ExpertInfo requireOwnExpert(Long userId) {
        ExpertInfo expert = expertInfoMapper.selectOne(Wrappers.<ExpertInfo>lambdaQuery()
                .eq(ExpertInfo::getUserId, userId));
        if (expert == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "仅专家可维护个人资料");
        }
        return expert;
    }

    /**
     * 三个可编辑字段全空时直接拒绝。
     *
     * <p>不这么做的话，一个空 JSON {@code {}} 也能返回 200「操作成功」，
     * 而用户看到的是"保存成功但没变化" —— 那是假成功，比报错更难查。</p>
     */
    private void requireSomethingToUpdate(String expertDomain, String introduction, String auditStatus) {
        if (!StringUtils.hasText(expertDomain)
                && introduction == null
                && !StringUtils.hasText(auditStatus)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请至少填写一项要修改的内容");
        }
    }

    /** 按 IN 批量取 t_user，避免逐行查库（N+1）。 */
    private Map<Long, User> loadUsers(List<ExpertInfo> experts) {
        Set<Long> userIds = experts.stream()
                .map(ExpertInfo::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));
    }

    /**
     * t_expert + t_user → {@link ExpertProfileVO}。
     *
     * <p>{@code realName} 兜底空串而不是 null，理由同 {@link #listExperts} 的注释。</p>
     */
    private ExpertProfileVO toProfileVO(ExpertInfo expert, User user) {
        ExpertProfileVO vo = new ExpertProfileVO();
        vo.setExpertId(expert.getExpertId());
        vo.setUserId(expert.getUserId());
        vo.setExpertDomain(expert.getExpertDomain());
        vo.setIntroduction(expert.getIntroduction());
        vo.setWorkloadStat(expert.getWorkloadStat());
        vo.setAuditStatus(expert.getAuditStatus());
        vo.setCreateTime(expert.getCreateTime());
        vo.setRealName(user == null || user.getRealName() == null ? "" : user.getRealName());
        vo.setAvatarUrl(user == null ? null : user.getAvatarUrl());
        return vo;
    }
}