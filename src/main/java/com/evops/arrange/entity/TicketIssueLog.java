package com.evops.arrange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 发证流水：打证（PRINT）、发放（ISSUE）每步经办与钟点 */
@Getter
@Setter
@TableName("t_ticket_issue_log")
public class TicketIssueLog extends BaseEntity {
    private Long ticketId;
    private String ticketNo;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime operateTime;
    private String remark;
}
