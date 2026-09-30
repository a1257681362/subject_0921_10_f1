package com.evops.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 自动填充 create_by/create_time/update_by/update_time，经办信息来自操作人上下文。 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Operator operator = OperatorContext.current();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", Long.class, operator.getId());
        strictInsertFill(metaObject, "updateBy", Long.class, operator.getId());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Operator operator = OperatorContext.current();
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", Long.class, operator.getId());
    }
}
