package com.evops.registration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 名册勘误痕迹：旧值保留，不抹除 */
@Getter
@Setter
@TableName("t_roster_correction")
public class RosterCorrection extends BaseEntity {
    private Long examineeId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String reason;
    private Long correctedBy;
    private String correctedName;
    private LocalDateTime correctedTime;
}
