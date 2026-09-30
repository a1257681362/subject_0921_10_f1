package com.evops.arrange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.arrange.entity.AdmissionTicket;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface AdmissionTicketMapper extends BaseMapper<AdmissionTicket> {

    /**
     * H2 全局序列取号：只增不回退，批次回退也不复用号段。
     * flushCache=TRUE 绕过 MyBatis 一级缓存——无参 select 在同一事务/会话内
     * 否则会被缓存成同一个号，造成批内证号撞号。
     */
    @Select("SELECT NEXT VALUE FOR seq_admission_ticket_no")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long nextTicketSequence();
}
