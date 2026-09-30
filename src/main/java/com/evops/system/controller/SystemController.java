package com.evops.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.ApiResponse;
import com.evops.system.entity.SchemaVersion;
import com.evops.system.mapper.SchemaVersionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作区版本：查看当前 schema 版本选择记录 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SchemaVersionMapper schemaVersionMapper;

    public SystemController(SchemaVersionMapper schemaVersionMapper) {
        this.schemaVersionMapper = schemaVersionMapper;
    }

    @GetMapping("/versions")
    public ApiResponse<List<SchemaVersion>> versions() {
        return ApiResponse.ok(schemaVersionMapper.selectList(
                new QueryWrapper<SchemaVersion>().orderByAsc("id")));
    }
}
