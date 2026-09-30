package com.evops.arrange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 编排批次：阶段结果（预览快照、各阶段经办钟点）与最终结论分开保存 */
@Getter
@Setter
@TableName("t_arrange_batch")
public class ArrangeBatch extends BaseEntity {
    private String batchNo;
    private String subject;
    private Integer totalCount;
    private Integer roomCount;
    private String seatStrategy;
    private String ticketNoPrefix;
    /** PREVIEWED / GENERATED / ROLLED_BACK */
    private String arrangeStatus;
    private String previewSnapshot;
    private Long previewBy;
    private String previewName;
    private LocalDateTime previewTime;
    private Long generatedBy;
    private String generatedName;
    private LocalDateTime generatedTime;
    private Long rollbackBy;
    private String rollbackName;
    private LocalDateTime rollbackTime;
    private String rollbackReason;
}
