package com.evops.registration.dto;

import lombok.Getter;
import lombok.Setter;

/** 逐行校验结果：可读、不丢行 */
@Getter
@Setter
public class RowCheckResult {
    private Integer lineNo;
    private String candidateNo;
    private String name;
    private String idNo;
    private boolean accepted;
    /** 拒收原因（可读），accepted=true 时为 null */
    private String reason;

    public static RowCheckResult accept(int lineNo, RosterImportRequest.Row row) {
        RowCheckResult r = base(lineNo, row);
        r.accepted = true;
        return r;
    }

    public static RowCheckResult reject(int lineNo, RosterImportRequest.Row row, String reason) {
        RowCheckResult r = base(lineNo, row);
        r.accepted = false;
        r.reason = reason;
        return r;
    }

    private static RowCheckResult base(int lineNo, RosterImportRequest.Row row) {
        RowCheckResult r = new RowCheckResult();
        r.lineNo = lineNo;
        r.candidateNo = row == null ? null : trim(row.getCandidateNo());
        r.name = row == null ? null : trim(row.getName());
        r.idNo = row == null ? null : trim(row.getIdNo());
        return r;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
