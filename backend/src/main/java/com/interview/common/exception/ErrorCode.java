package com.interview.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    RESUME_UPLOAD_FAILED(2001, "简历上传失败"),
    RESUME_PARSE_FAILED(2002, "简历解析失败"),
    AI_SERVICE_ERROR(7001, "AI服务调用失败");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
