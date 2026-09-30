package com.evops.common;

/**
 * 业务异常：消息面向经办人员可读（例如“证号撞号，整批已拒收”），
 * 由 GlobalExceptionHandler 统一转成带 HTTP 状态码的 ApiResponse。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    public static BusinessException unprocessable(String message) {
        return new BusinessException(ErrorCode.UNPROCESSABLE, message);
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
