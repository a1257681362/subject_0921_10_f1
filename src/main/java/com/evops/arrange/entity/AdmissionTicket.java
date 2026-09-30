package com.evops.arrange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 准考证：一人一条，证号全局唯一，含考场、座位 */
@Getter
@Setter
@TableName("t_admission_ticket")
public class AdmissionTicket extends BaseEntity {
    private String ticketNo;
    private Long arrangeBatchId;
    private String arrangeBatchNo;
    private Long examineeId;
    private String candidateNo;
    private String name;
    private String idType;
    private String idNo;
    private String subject;
    private Long siteId;
    private String siteCode;
    private String siteName;
    private String siteLocation;
    private Long roomId;
    private String roomCode;
    private String roomLocation;
    private Integer seatNo;
    /** GENERATED / PRINTED / ISSUED */
    private String ticketStatus;
    private Long generatedBy;
    private String generatedName;
    private LocalDateTime generatedTime;
    private Long printedBy;
    private String printedName;
    private LocalDateTime printedTime;
    private Long issuedBy;
    private String issuedName;
    private LocalDateTime issuedTime;
}
