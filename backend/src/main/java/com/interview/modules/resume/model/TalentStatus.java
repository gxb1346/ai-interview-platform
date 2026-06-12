package com.interview.modules.resume.model;

/**
 * 人才库管道状态
 */
public enum TalentStatus {
    NEW,              // 新入库（待评估）
    INVITED,          // 已邀约
    WAITING_INTERVIEW, // 待面试
    PASSED,           // 面试通过
    REJECTED          // 不合适
}
