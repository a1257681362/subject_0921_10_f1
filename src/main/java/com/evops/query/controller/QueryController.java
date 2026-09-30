package com.evops.query.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.mapper.AdmissionTicketMapper;
import com.evops.common.ApiResponse;
import com.evops.query.dto.TicketDossier;
import com.evops.query.service.QueryService;
import com.evops.registration.entity.Examinee;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 查档步：按证号查、按姓名查，一人一档 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;
    private final AdmissionTicketMapper ticketMapper;

    public QueryController(QueryService queryService, AdmissionTicketMapper ticketMapper) {
        this.queryService = queryService;
        this.ticketMapper = ticketMapper;
    }

    /** 按证号查：一人一档（准考证 + 打证发放轨迹 + 勘误痕迹） */
    @GetMapping("/tickets/{ticketNo}")
    public ApiResponse<TicketDossier> byTicketNo(@PathVariable("ticketNo") String ticketNo) {
        return ApiResponse.ok(queryService.getByTicketNo(ticketNo));
    }

    /** 按姓名查准考证（模糊匹配） */
    @GetMapping("/tickets")
    public ApiResponse<List<AdmissionTicket>> tickets(@RequestParam("name") String name) {
        return ApiResponse.ok(queryService.searchTicketsByName(name));
    }

    /** 按姓名查报名名册（覆盖尚未出证的考生） */
    @GetMapping("/examinees")
    public ApiResponse<List<Examinee>> examinees(@RequestParam("name") String name) {
        return ApiResponse.ok(queryService.searchExamineesByName(name));
    }

    /** 列出准考证，可按编排批次/状态过滤 */
    @GetMapping("/all-tickets")
    public ApiResponse<List<AdmissionTicket>> allTickets(
            @RequestParam(value = "arrangeBatchNo", required = false) String arrangeBatchNo,
            @RequestParam(value = "status", required = false) String status) {
        QueryWrapper<AdmissionTicket> wrapper = new QueryWrapper<AdmissionTicket>()
                .orderByAsc("arrange_batch_no", "ticket_no");
        if (arrangeBatchNo != null && !arrangeBatchNo.trim().isEmpty()) {
            wrapper.eq("arrange_batch_no", arrangeBatchNo.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("ticket_status", status.trim());
        }
        return ApiResponse.ok(ticketMapper.selectList(wrapper));
    }

    @GetMapping("/examinees/{id}")
    public ApiResponse<Examinee> examinee(@PathVariable("id") Long id) {
        return ApiResponse.ok(queryService.getExaminee(id));
    }
}
