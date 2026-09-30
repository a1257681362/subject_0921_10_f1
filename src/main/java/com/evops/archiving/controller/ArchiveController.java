package com.evops.archiving.controller;

import com.evops.archiving.entity.ExamRoom;
import com.evops.archiving.entity.ExamSite;
import com.evops.archiving.service.ArchiveService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 建档步：考点、考场建档（容量、位置） */
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/sites")
    public ApiResponse<ExamSite> createSite(@RequestBody ExamSite request) {
        return ApiResponse.ok(archiveService.createSite(request));
    }

    @GetMapping("/sites")
    public ApiResponse<List<ExamSite>> listSites() {
        return ApiResponse.ok(archiveService.listSites());
    }

    @GetMapping("/sites/{id}")
    public ApiResponse<ExamSite> getSite(@PathVariable("id") Long id) {
        return ApiResponse.ok(archiveService.getSite(id));
    }

    @PostMapping("/sites/{id}/rooms")
    public ApiResponse<ExamRoom> createRoom(@PathVariable("id") Long siteId,
                                            @RequestBody ExamRoom request) {
        return ApiResponse.ok(archiveService.createRoom(siteId, request));
    }

    @GetMapping("/rooms")
    public ApiResponse<List<ExamRoom>> listRooms(@RequestParam(value = "siteId", required = false) Long siteId) {
        return ApiResponse.ok(archiveService.listRooms(siteId));
    }
}
