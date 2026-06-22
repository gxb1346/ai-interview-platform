package com.interview.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // ---- 用户相关 ----
    USERNAME_EXISTS(1000, "用户名已存在"),
    ACCOUNT_DISABLED(1001, "账户已被禁用"),
    USER_NOT_FOUND(1002, "用户不存在"),
    BAD_CREDENTIALS(1003, "用户名或密码错误"),
    PASSWORD_WRONG(1004, "原密码错误"),
    PASSWORD_SAME_AS_OLD(1005, "新密码不能与旧密码相同"),
    TOKEN_REFRESH_FAILED(1006, "令牌刷新失败"),

    // ---- 面试会话相关 ----
    SESSION_NOT_FOUND(2000, "面试会话不存在"),
    SESSION_STATUS_ERROR(2001, "面试会话状态异常"),
    SESSION_CANNOT_PAUSE(2002, "只能暂停进行中的面试"),
    SESSION_CANNOT_RESUME(2003, "只能恢复已暂停的面试"),
    SESSION_CANNOT_START(2004, "只能启动准备就绪的面试"),
    SESSION_CANNOT_ANSWER(2005, "只能在进行中的面试中作答"),
    SESSION_ALREADY_COMPLETED(2006, "面试已结束，无法操作"),

    // ---- 简历相关 ----
    RESUME_UPLOAD_FAILED(3001, "简历上传失败"),
    RESUME_PARSE_FAILED(3002, "简历解析失败"),
    RESUME_NOT_FOUND(3003, "简历不存在"),
    TALENT_STATUS_INVALID(3004, "无效的人才库状态"),

    // ---- 知识库相关 ----
    DOCUMENT_NOT_FOUND(4000, "文档不存在"),
    DOCUMENT_PARSE_FAILED(4001, "文档解析失败"),
    DOCUMENT_EMPTY(4002, "文档内容为空"),
    DOCUMENT_CONTENT_TOO_SHORT(4003, "文档内容太少，无法分块"),

    // ---- 基础设施 ----
    REDIS_OPERATION_FAILED(5000, "Redis操作失败"),
    JSON_SERIALIZE_FAILED(5001, "JSON序列化失败"),
    FILE_UPLOAD_FAILED(5002, "文件上传失败"),
    MD5_COMPUTE_FAILED(5003, "MD5计算失败"),

    // ---- AI 服务相关 ----
    AI_SERVICE_ERROR(6000, "AI服务调用失败"),
    AI_RATE_LIMITED(6001, "AI调用限流，请稍后重试"),
    AI_EMPTY_RESPONSE(6002, "AI返回为空"),

    // ---- TTS 相关 ----
    TTS_SERVICE_ERROR(7000, "TTS服务调用失败"),
    TTS_HTTP_ERROR(7001, "TTS HTTP请求失败"),
    TTS_RESPONSE_MISSING_AUDIO(7002, "TTS响应缺少音频数据"),

    // ---- 向量化相关 ----
    VECTORIZATION_FAILED(8000, "向量化失败"),
    DOCUMENT_INDEX_FAILED(8001, "文档索引失败"),

    // ---- 任务相关 ----
    TASK_CREATE_FAILED(9000, "任务创建失败"),
    TASK_PROCESS_FAILED(9001, "任务处理失败"),

    // ---- 会话存储相关 ----
    SESSION_SAVE_FAILED(10000, "会话存储失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}