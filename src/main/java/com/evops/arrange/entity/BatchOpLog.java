package com.evops.arrange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 批次操作日志：预览/生成/回退全程留痕，状态回退可追溯 */
@Getter
@Setter
@TableName("t_batch_op_log")
public class BatchOpLog extends BaseEntity {
    private Long batchId;
    private String batchNo;
    /** PREVIEW / GENERATE / ROLLBACK */
    private String action;
    private String detail;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime operateTime;
}
