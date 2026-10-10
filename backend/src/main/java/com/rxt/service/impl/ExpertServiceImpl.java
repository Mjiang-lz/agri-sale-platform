package com.rxt.service.impl;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.AppointmentDTO;
import com.rxt.entity.Appointment;
import com.rxt.entity.ExpertInfo;
import com.rxt.mapper.AppointmentMapper;
import com.rxt.mapper.ExpertInfoMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.QuestionMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.entity.User;
import com.rxt.service.ExpertService;
import com.rxt.vo.AppointmentVO;
import com.rxt.vo.ExpertVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpertServiceImpl implements ExpertService {

    /** 审核结论词表里的「通过」。全仓没有共享常量类，各服务各自持有（同 HomeServiceImpl:49）。 */
    private static final String AUDIT_APPROVED = "已通过";

    @Autowired private KnowledgeMapper knowledgeMapper;
    @Autowired private QuestionMapper questionMapper;
    @Autowired private AppointmentMapper appointmentMapper;
    @Autowired private ExpertInfoMapper expertInfoMapper;
    @Autowired private UserMapper userMapper;


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

        Set<Long> userIds = experts.stream()
                .map(ExpertInfo::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));

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
}