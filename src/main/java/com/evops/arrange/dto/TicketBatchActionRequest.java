package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 批量打证/发放请求：三种圈定方式 */
@Getter
@Setter
public class TicketBatchActionRequest {
    /** 直接指定证号 */
    private List<String> ticketNos;
    /** 按编排批次圈定 */
    private String arrangeBatchNo;
    /** PRINT 动作只处理该状态（默认 GENERATED）；ISSUE 默认 PRINTED */
    private String expectStatus;
    private String remark;
}
