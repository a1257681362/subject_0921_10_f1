package com.evops.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 工作区 schema 版本登记：版本选择可追溯 */
@Getter
@Setter
@TableName("t_evops_schema_version")
public class SchemaVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionNo;
    private String description;
    private LocalDateTime createdAt;
}
