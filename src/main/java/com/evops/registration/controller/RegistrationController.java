package com.evops.registration.controller;

import com.evops.common.ApiResponse;
import com.evops.registration.dto.RosterImportRequest;
import com.evops.registration.dto.RosterImportResult;
import com.evops.registration.entity.Examinee;
import com.evops.registration.entity.RosterCorrection;
import com.evops.registration.service.RegistrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 报名步：名册挂入（批量动作先预览影响范围）、勘误留痕 */
@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** 预览名册导入影响范围：不落库，逐行给出接收/拒收与可读原因 */
    @PostMapping("/roster/preview")
    public ApiResponse<RosterImportResult> preview(@RequestBody RosterImportRequest request) {
        return ApiResponse.ok(registrationService.importRoster(request, true));
    }

    /** 执行名册导入：仅挂入通过校验的行，返回实际影响条数 */
    @PostMapping("/roster/import")
    public ApiResponse<RosterImportResult> importRoster(@RequestBody RosterImportRequest request) {
        return ApiResponse.ok(registrationService.importRoster(request, false));
    }

    @GetMapping("/roster/{batchNo}")
    public ApiResponse<List<Examinee>> listByBatch(@PathVariable("batchNo") String batchNo) {
        return ApiResponse.ok(registrationService.listByBatch(batchNo));
    }

    @GetMapping("/examinees/{id}")
    public ApiResponse<Examinee> getExaminee(@PathVariable("id") Long id) {
        return ApiResponse.ok(registrationService.getExaminee(id));
    }

    /** 名册勘误：旧值保留并记录更正痕迹 */
    @PostMapping("/examinees/{id}/corrections")
    public ApiResponse<RosterCorrection> correct(@PathVariable("id") Long id,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(registrationService.correct(
                id, body.get("fieldName"), body.get("newValue"), body.get("reason")));
    }

    @GetMapping("/examinees/{id}/corrections")
    public ApiResponse<List<RosterCorrection>> listCorrections(@PathVariable("id") Long id) {
        return ApiResponse.ok(registrationService.listCorrections(id));
    }
}
