package com.evops.registration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 名册导入影响范围与结果。dryRun=true 时只展示影响范围不落库；
 * dryRun=false 时 insertedRows 为实际挂入条数。
 */
@Getter
@Setter
public class RosterImportResult {
    private boolean dryRun;
    private String batchNo;
    private String subject;
    private int totalRows;
    private int acceptedCount;
    private int rejectedCount;
    private int insertedRows;
    private List<RowCheckResult> lines;
}
