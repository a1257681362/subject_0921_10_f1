package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编排预览（影响范围）。
 * blockers 非空时 readyToGenerate=false，执行端直接拒收。
 */
@Getter
@Setter
public class ArrangePreview {
    private Long batchId;
    private String batchNo;
    private String arrangeStatus;
    private boolean readyToGenerate;
    private int candidateCount;
    private int roomCount;
    private int totalCapacity;
    private int capacityShortage;
    private List<String> blockers;
    private List<PlannedRoom> rooms;
    private List<PlannedCandidate> candidates;
}
