package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

/** 预览快照中的单人编排项（阶段结果，执行时照此落准考证） */
@Getter
@Setter
public class PlannedCandidate {
    private Long examineeId;
    private String candidateNo;
    private String name;
    private String idType;
    private String idNo;
    private String subject;
    private String ticketNo;
    private boolean externalTicketNo;
    private Long siteId;
    private String siteCode;
    private String siteName;
    private Long roomId;
    private String roomCode;
    private String roomLocation;
    private Integer seatNo;
}
