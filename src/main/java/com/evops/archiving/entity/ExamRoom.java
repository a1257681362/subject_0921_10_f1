package com.evops.archiving.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 考场建档（归属考点，含容量与位置） */
@Getter
@Setter
@TableName("t_exam_room")
public class ExamRoom extends BaseEntity {
    private Long siteId;
    private String roomCode;
    private String roomName;
    private String floorNo;
    private String location;
    private Integer capacity;
    private String status;
}
