package com.evops.archiving.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 考点建档 */
@Getter
@Setter
@TableName("t_exam_site")
public class ExamSite extends BaseEntity {
    private String siteCode;
    private String siteName;
    private String location;
    private String contactPerson;
    private String contactPhone;
    private String remark;
    private String status;
}
