package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 预览快照（阶段结果）：执行时以此为准，落为准考证最终结论 */
@Getter
@Setter
public class ArrangeSnapshot {
    private String rosterBatchNo;
    private String subject;
    private String ticketNoPrefix;
    private int candidateCount;
    private int roomCount;
    private int totalCapacity;
    private boolean externalOnly;
    /** 预览时的阻塞项；非空表示方案不可执行，generate 直接拒收 */
    private List<String> blockers;
    private List<PlannedRoom> rooms;
    private List<PlannedCandidate> candidates;
}
