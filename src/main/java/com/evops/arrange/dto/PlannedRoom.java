package com.evops.arrange.dto;

import lombok.Getter;
import lombok.Setter;

/** 预览快照中的考场占用项 */
@Getter
@Setter
public class PlannedRoom {
    private Long roomId;
    private Long siteId;
    private String siteCode;
    private String siteName;
    private String roomCode;
    private String roomName;
    private String roomLocation;
    private Integer capacity;
    private Integer assignedCount;
    private Integer seatFrom;
    private Integer seatTo;
}
