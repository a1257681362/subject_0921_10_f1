package com.evops.common;

/** 当前操作人（经办）。id 取自本地账户映射，name 为登录名，写入各业务留痕字段。 */
public class Operator {

    private final Long id;
    private final String name;

    public Operator(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
