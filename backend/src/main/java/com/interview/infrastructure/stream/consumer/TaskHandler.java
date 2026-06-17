package com.interview.infrastructure.stream.consumer;

import com.interview.infrastructure.stream.model.StreamMessage;

/**
 * 任务处理器接口
 * 每种 TaskType 对应一个实现，处理具体的业务逻辑
 *
 * @see com.interview.infrastructure.stream.model.TaskType
 */
public interface TaskHandler {

    /**
     * 处理任务
     *
     * @param message  Stream 消息
     * @return true=处理成功，false=处理失败（框架自动重试）
     */
    boolean handle(StreamMessage message);
}
