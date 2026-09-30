package com.evops.registration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 名册批量导入请求 */
@Getter
@Setter
public class RosterImportRequest {
    /** 可空，缺省由服务端生成报名批次号 */
    private String batchNo;
    private String subject;
    private List<Row> rows;

    @Getter
    @Setter
    public static class Row {
        private String candidateNo;
        private String name;
        private String gender;
        private String idType;
        private String idNo;
        private String phone;
        private String schoolOrOrg;
        private String subject;
    }
}
