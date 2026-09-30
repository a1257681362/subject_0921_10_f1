package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 编排请求：圈定待编排考生 + 可选的外部准考证号清单。
 * ticketNos 以报名号 candidateNo 为键；未提供则用全局序列自动出号。
 */
@Getter
@Setter
public class ArrangeRequest {
    /** 按报名批次圈定（可空） */
    private String rosterBatchNo;
    /** 按报考科目圈定（可空，与批次同时给时取交集） */
    private String subject;
    /** 序列出号时的前缀，可空 */
    private String ticketNoPrefix;
    /** 外部下发的准考证号（可空）：candidateNo -> ticketNo */
    private Map<String, String> ticketNos;
}
