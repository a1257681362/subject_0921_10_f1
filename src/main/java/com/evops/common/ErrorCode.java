package com.evops.common;

/**
 * 可读业务错误码：接口失败时携带 code 与 HTTP 状态，调用方看到的是可读状态而非空白。
 */
public enum ErrorCode {

    BAD_REQUEST("BAD_REQUEST", 400),
    NOT_FOUND("NOT_FOUND", 404),
    CONFLICT("CONFLICT", 409),                 // 撞号、状态冲突等
    UNPROCESSABLE("UNPROCESSABLE", 422),       // 批量内行级校验不通过
    UNAUTHORIZED("UNAUTHORIZED", 401),
    FORBIDDEN("FORBIDDEN", 403);

    private final String code;
    private final int httpStatus;

    ErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
