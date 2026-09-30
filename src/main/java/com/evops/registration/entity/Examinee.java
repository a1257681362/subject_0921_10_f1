package com.evops.registration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 报名名册中的考生（当前值；勘误旧值见 RosterCorrection） */
@Getter
@Setter
@TableName("t_examinee")
public class Examinee extends BaseEntity {
    private String batchNo;
    private Integer lineNo;
    private String candidateNo;
    private String name;
    private String gender;
    private String idType;
    private String idNo;
    private String phone;
    private String schoolOrOrg;
    private String subject;
    private String status;
}
