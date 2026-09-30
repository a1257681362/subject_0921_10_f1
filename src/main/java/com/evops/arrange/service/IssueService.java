package com.evops.arrange.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.arrange.dto.TicketActionResult;
import com.evops.arrange.dto.TicketBatchActionRequest;
import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.entity.TicketIssueLog;
import com.evops.arrange.mapper.AdmissionTicketMapper;
import com.evops.arrange.mapper.TicketIssueLogMapper;
import com.evops.common.BusinessException;
import com.evops.common.Operator;
import com.evops.common.OperatorContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发证步：打证（GENERATED→PRINTED）、发放（PRINTED→ISSUED）两步。
 * 每步写 t_ticket_issue_log，留经办与钟点；批量动作先预览影响范围。
 */
@Service
public class IssueService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdmissionTicketMapper ticketMapper;
    private final TicketIssueLogMapper issueLogMapper;

    public IssueService(AdmissionTicketMapper ticketMapper, TicketIssueLogMapper issueLogMapper) {
        this.ticketMapper = ticketMapper;
        this.issueLogMapper = issueLogMapper;
    }

    /** 批量动作预览或执行。dryRun=true 仅展示影响范围，不落状态、不写流水。 */
    @Transactional
    public TicketActionResult act(TicketBatchActionRequest request, String action, boolean dryRun) {
        boolean print = "PRINT".equals(action);
        if (!print && !"ISSUE".equals(action)) {
            throw BusinessException.badRequest("不支持的发证动作：" + action);
        }
        String expectStatus = request.getExpectStatus() != null && !request.getExpectStatus().trim().isEmpty()
                ? request.getExpectStatus().trim()
                : (print ? "GENERATED" : "PRINTED");
        String targetStatus = print ? "PRINTED" : "ISSUED";

        List<AdmissionTicket> candidates = loadCandidates(request);

        TicketActionResult result = new TicketActionResult();
        result.setDryRun(dryRun);
        result.setAction(action);
        result.setTotalCandidates(candidates.size());
        List<TicketActionResult.Item> processed = new ArrayList<>();
        List<TicketActionResult.Skipped> skipped = new ArrayList<>();

        Operator operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        for (AdmissionTicket ticket : candidates) {
            if (!expectStatus.equals(ticket.getTicketStatus())) {
                TicketActionResult.Skipped skip = new TicketActionResult.Skipped();
                skip.setTicketNo(ticket.getTicketNo());
                skip.setName(ticket.getName());
                skip.setReason("当前状态 " + ticket.getTicketStatus() + "，非 " + expectStatus + "，跳过");
                skipped.add(skip);
                continue;
            }
            if (!dryRun) {
                String fromStatus = ticket.getTicketStatus();
                ticket.setTicketStatus(targetStatus);
                if (print) {
                    ticket.setPrintedBy(operator.getId());
                    ticket.setPrintedName(operator.getName());
                    ticket.setPrintedTime(now);
                } else {
                    ticket.setIssuedBy(operator.getId());
                    ticket.setIssuedName(operator.getName());
                    ticket.setIssuedTime(now);
                }
                ticketMapper.updateById(ticket);

                TicketIssueLog logEntry = new TicketIssueLog();
                logEntry.setTicketId(ticket.getId());
                logEntry.setTicketNo(ticket.getTicketNo());
                logEntry.setAction(action);
                logEntry.setFromStatus(fromStatus);
                logEntry.setToStatus(targetStatus);
                logEntry.setOperatorId(operator.getId());
                logEntry.setOperatorName(operator.getName());
                logEntry.setOperateTime(now);
                logEntry.setRemark(request.getRemark());
                issueLogMapper.insert(logEntry);
            }
            TicketActionResult.Item item = new TicketActionResult.Item();
            item.setTicketNo(ticket.getTicketNo());
            item.setName(ticket.getName());
            item.setFromStatus(expectStatus);
            item.setToStatus(targetStatus);
            item.setOperatorName(operator.getName());
            item.setOperateTime(TIME_FMT.format(now));
            processed.add(item);
        }

        result.setWillProcessCount(processed.size());
        result.setProcessedCount(dryRun ? 0 : processed.size());
        result.setProcessed(processed);
        result.setSkipped(skipped);
        return result;
    }

    public AdmissionTicket getByTicketNo(String ticketNo) {
        if (ticketNo == null || ticketNo.trim().isEmpty()) {
            throw BusinessException.badRequest("准考证号不能为空");
        }
        AdmissionTicket ticket = ticketMapper.selectOne(new QueryWrapper<AdmissionTicket>()
                .eq("ticket_no", ticketNo.trim()), false);
        if (ticket == null) {
            throw BusinessException.notFound("准考证不存在：" + ticketNo);
        }
        return ticket;
    }

    public List<TicketIssueLog> listLogs(String ticketNo) {
        AdmissionTicket ticket = getByTicketNo(ticketNo);
        return issueLogMapper.selectList(new QueryWrapper<TicketIssueLog>()
                .eq("ticket_id", ticket.getId()).orderByAsc("id"));
    }

    // ------------------------------------------------------------------

    private List<AdmissionTicket> loadCandidates(TicketBatchActionRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        Set<String> ticketNos = request.getTicketNos() == null ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.getTicketNos());
        boolean hasBatch = request.getArrangeBatchNo() != null
                && !request.getArrangeBatchNo().trim().isEmpty();
        if (ticketNos.isEmpty() && !hasBatch) {
            throw BusinessException.badRequest("请通过 ticketNos 或 arrangeBatchNo 指定处理范围");
        }
        QueryWrapper<AdmissionTicket> wrapper = new QueryWrapper<AdmissionTicket>()
                .orderByAsc("ticket_no");
        if (!ticketNos.isEmpty()) {
            wrapper.in("ticket_no", ticketNos);
        }
        if (hasBatch) {
            wrapper.eq("arrange_batch_no", request.getArrangeBatchNo().trim());
        }
        return ticketMapper.selectList(wrapper);
    }
}
