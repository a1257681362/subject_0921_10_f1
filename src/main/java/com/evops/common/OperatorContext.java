package com.evops.common;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作人上下文：从 Shiro 会话取登录名。未登录场景（系统任务）记为 system。
 * 账户表后续接入时，只需扩展 USER_IDS 映射。
 */
public final class OperatorContext {

    private static final Map<String, Long> USER_IDS = new HashMap<>();

    static {
        USER_IDS.put("bootstrap", 1L);
    }

    private OperatorContext() {
    }

    public static Operator current() {
        String name = "system";
        try {
            Subject subject = SecurityUtils.getSubject();
            Object principal = subject != null ? subject.getPrincipal() : null;
            if (principal != null) {
                name = principal.toString();
            }
        } catch (Exception ignored) {
            // 无会话上下文时回落为系统操作
        }
        return new Operator(USER_IDS.getOrDefault(name, 0L), name);
    }
}
