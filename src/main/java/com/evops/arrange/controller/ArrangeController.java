package com.evops.arrange.controller;

import com.evops.arrange.dto.ArrangePreview;
import com.evops.arrange.dto.ArrangeRequest;
import com.evops.arrange.entity.ArrangeBatch;
import com.evops.arrange.entity.BatchOpLog;
import com.evops.arrange.service.ArrangeService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 编排步：先预览影响范围（阶段结果），再按批次生成准考证（最终结论），可回退且全程留痕。
 */
@RestController
@RequestMapping("/api/arrange")
public class ArrangeController {

    private final ArrangeService arrangeService;

    public ArrangeController(ArrangeService arrangeService) {
        this.arrangeService = arrangeService;
    }

    /** 预览：展示影响范围与座位方案，登记 PREVIEWED 批次，不落准考证 */
    @PostMapping("/batches/preview")
    public ApiResponse<ArrangePreview> preview(@RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.preview(request));
    }

    /** 执行：按预览批次生成准考证；证号撞号整批拒收 */
    @PostMapping("/batches/{batchNo}/generate")
    public ApiResponse<ArrangePreview> generate(@PathVariable("batchNo") String batchNo) {
        return ApiResponse.ok(arrangeService.generate(batchNo));
    }

    /** 回退：撤销批次准考证，批次与日志保留可追溯 */
    @PostMapping("/batches/{batchNo}/rollback")
    public ApiResponse<ArrangeBatch> rollback(@PathVariable("batchNo") String batchNo,
                                              @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(arrangeService.rollback(batchNo, reason));
    }

    @GetMapping("/batches")
    public ApiResponse<List<ArrangeBatch>> listBatches() {
        return ApiResponse.ok(arrangeService.listBatches());
    }

    @GetMapping("/batches/{batchNo}")
    public ApiResponse<ArrangePreview> getBatch(@PathVariable("batchNo") String batchNo) {
        return ApiResponse.ok(arrangeService.getPreview(batchNo));
    }

    @GetMapping("/batches/{batchNo}/logs")
    public ApiResponse<List<BatchOpLog>> listLogs(@PathVariable("batchNo") String batchNo) {
        return ApiResponse.ok(arrangeService.listBatchLogs(batchNo));
    }
}
