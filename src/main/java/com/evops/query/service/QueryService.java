package com.evops.query.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.entity.TicketIssueLog;
import com.evops.arrange.mapper.AdmissionTicketMapper;
import com.evops.arrange.mapper.TicketIssueLogMapper;
import com.evops.common.BusinessException;
import com.evops.query.dto.TicketDossier;
import com.evops.registration.entity.Examinee;
import com.evops.registration.entity.RosterCorrection;
import com.evops.registration.mapper.ExamineeMapper;
import com.evops.registration.mapper.RosterCorrectionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查档步：按证号精确查、按姓名模糊查；返回一人一档，含发证轨迹与勘误痕迹。
 */
@Service
public class QueryService {

    private final AdmissionTicketMapper ticketMapper;
    private final TicketIssueLogMapper issueLogMapper;
    private final ExamineeMapper examineeMapper;
    private final RosterCorrectionMapper correctionMapper;

    public QueryService(AdmissionTicketMapper ticketMapper, TicketIssueLogMapper issueLogMapper,
                        ExamineeMapper examineeMapper, RosterCorrectionMapper correctionMapper) {
        this.ticketMapper = ticketMapper;
        this.issueLogMapper = issueLogMapper;
        this.examineeMapper = examineeMapper;
        this.correctionMapper = correctionMapper;
    }

    /** 按证号查：一人一档 */
    public TicketDossier getByTicketNo(String ticketNo) {
        if (ticketNo == null || ticketNo.trim().isEmpty()) {
            throw BusinessException.badRequest("准考证号不能为空");
        }
        AdmissionTicket ticket = ticketMapper.selectOne(new QueryWrapper<AdmissionTicket>()
                .eq("ticket_no", ticketNo.trim()), false);
        if (ticket == null) {
            throw BusinessException.notFound("未查到该证号的准考证：" + ticketNo);
        }
        return buildDossier(ticket);
    }

    /** 按姓名查：姓名模糊匹配准考证 */
    public List<AdmissionTicket> searchTicketsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.badRequest("姓名查询条件不能为空");
        }
        return ticketMapper.selectList(new QueryWrapper<AdmissionTicket>()
                .like("name", name.trim()).orderByAsc("ticket_no"));
    }

    /** 按姓名查名册：报名后尚未编排发证的考生也能查到 */
    public List<Examinee> searchExamineesByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.badRequest("姓名查询条件不能为空");
        }
        return examineeMapper.selectList(new QueryWrapper<Examinee>()
                .like("name", name.trim()).orderByAsc("batch_no", "line_no"));
    }

    public Examinee getExaminee(Long examineeId) {
        Examinee examinee = examineeMapper.selectById(examineeId);
        if (examinee == null) {
            throw BusinessException.notFound("名册记录不存在：id=" + examineeId);
        }
        return examinee;
    }

    private TicketDossier buildDossier(AdmissionTicket ticket) {
        TicketDossier dossier = new TicketDossier();
        dossier.setTicket(ticket);
        dossier.setIssueLogs(issueLogMapper.selectList(new QueryWrapper<TicketIssueLog>()
                .eq("ticket_id", ticket.getId()).orderByAsc("id")));
        dossier.setCorrections(correctionMapper.selectList(new QueryWrapper<RosterCorrection>()
                .eq("examinee_id", ticket.getExamineeId()).orderByDesc("corrected_time")));
        return dossier;
    }
}
