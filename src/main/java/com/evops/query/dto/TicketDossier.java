package com.evops.query.dto;

import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.entity.TicketIssueLog;
import com.evops.registration.entity.RosterCorrection;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 一人一档：准考证（最终结论）+ 打证发放轨迹 + 名册勘误痕迹 */
@Getter
@Setter
public class TicketDossier {
    private AdmissionTicket ticket;
    private List<TicketIssueLog> issueLogs;
    private List<RosterCorrection> corrections;
}
