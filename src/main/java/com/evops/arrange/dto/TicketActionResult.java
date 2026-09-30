package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 打证/发放批量动作的影响范围与结果（dryRun=true 只展示） */
@Getter
@Setter
public class TicketActionResult {
    private boolean dryRun;
    /** PRINT / ISSUE */
    private String action;
    private int totalCandidates;
    private int willProcessCount;
    private int processedCount;
    private List<Item> processed;
    private List<Skipped> skipped;

    @Getter
    @Setter
    public static class Item {
        private String ticketNo;
        private String name;
        private String fromStatus;
        private String toStatus;
        private String operatorName;
        private String operateTime;
    }

    @Getter
    @Setter
    public static class Skipped {
        private String ticketNo;
        private String name;
        private String reason;
    }
}
