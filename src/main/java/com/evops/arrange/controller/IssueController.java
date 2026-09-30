package com.evops.arrange.controller;

import com.evops.arrange.dto.TicketActionResult;
import com.evops.arrange.dto.TicketBatchActionRequest;
import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.entity.TicketIssueLog;
import com.evops.arrange.service.IssueService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发证步：打证、发放两步，均先预览影响范围再执行，每步留经办与钟点。
 */
@RestController
@RequestMapping("/api/issue")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    // 打证 -----------------------------------------------------------

    @PostMapping("/print/preview")
    public ApiResponse<TicketActionResult> printPreview(@RequestBody TicketBatchActionRequest request) {
        return ApiResponse.ok(issueService.act(request, "PRINT", true));
    }

    @PostMapping("/print/execute")
    public ApiResponse<TicketActionResult> printExecute(@RequestBody TicketBatchActionRequest request) {
        return ApiResponse.ok(issueService.act(request, "PRINT", false));
    }

    // 发放 -----------------------------------------------------------

    @PostMapping("/distribute/preview")
    public ApiResponse<TicketActionResult> distributePreview(@RequestBody TicketBatchActionRequest request) {
        return ApiResponse.ok(issueService.act(request, "ISSUE", true));
    }

    @PostMapping("/distribute/execute")
    public ApiResponse<TicketActionResult> distributeExecute(@RequestBody TicketBatchActionRequest request) {
        return ApiResponse.ok(issueService.act(request, "ISSUE", false));
    }

    // 单张查证与轨迹 --------------------------------------------------

    @GetMapping("/tickets/{ticketNo}")
    public ApiResponse<AdmissionTicket> getTicket(@PathVariable("ticketNo") String ticketNo) {
        return ApiResponse.ok(issueService.getByTicketNo(ticketNo));
    }

    @GetMapping("/tickets/{ticketNo}/logs")
    public ApiResponse<List<TicketIssueLog>> getLogs(@PathVariable("ticketNo") String ticketNo) {
        return ApiResponse.ok(issueService.listLogs(ticketNo));
    }
}
