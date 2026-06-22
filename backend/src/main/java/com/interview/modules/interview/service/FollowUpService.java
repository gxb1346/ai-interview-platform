package com.interview.modules.interview.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.InterviewQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 智能追问服务
 * 支持按面试阶段生成差异化追问和过渡语
 */
@Slf4j
@Service
public class FollowUpService {

    // AI 调用最大重试次数
    private static final int AI_MAX_RETRIES = 2;
    // AI 调用重试间隔（毫秒）
    private static final long AI_RETRY_DELAY_MS = 800;

    private final ChatClient chatClient;

    public FollowUpService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个面试官，善于根据候选人的回答进行恰当追问。")
                .build();
    }

    /**
     * 带重试的 AI 调用
     */
    private String callAiWithRetry(String prompt, String callType) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= AI_MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("AI {} 重试 {}/{}", callType, attempt, AI_MAX_RETRIES);
                    Thread.sleep(AI_RETRY_DELAY_MS);
                }
                return chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI调用被中断");
            } catch (Exception e) {
                lastException = e;
                log.warn("AI {} 第{}次调用失败: {}", callType, attempt + 1, e.getMessage());
            }
        }
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI " + callType + " 所有重试均失败");
    }

    /**
     * 生成智能追问
     *
     * @param lastAnswer    候选人的上一条回答
     * @param question      当前面试题目
     * @param followUpIndex 当前是第几轮追问（从 1 开始）
     * @param stage         当前面试阶段
     * @return 追问内容
     */
    public String generateFollowUp(String lastAnswer, InterviewQuestion question, int followUpIndex, String stage) {
        try {
            String prompt = buildFollowUpPrompt(lastAnswer, question, followUpIndex, stage);
            return callAiWithRetry(prompt, "追问");
        } catch (Exception e) {
            log.warn("生成追问失败，使用fallback: {}", e.getMessage());
            return getFallbackFollowUp(stage, followUpIndex);
        }
    }

    private String buildFollowUpPrompt(String lastAnswer, InterviewQuestion question, int followUpIndex, String stage) {
        String stageInstruction = getStageFollowUpInstruction(stage);
        return String.format("""
                你是一个面试官，正在对候选人进行追问。
                
                当前面试阶段：%s
                本轮是第 %d 轮追问。
                原始问题：%s
                候选人回答：%s
                
                %s
                
                请给出面试官的发言：
                """, getStageLabel(stage), followUpIndex, question.getText(), lastAnswer, stageInstruction);
    }

    private String getStageFollowUpInstruction(String stage) {
        if (stage == null) stage = "";
        return switch (stage) {
            case "selfIntro" -> """
                要求：
                1. 这是自我介绍环节，追问应围绕候选人的背景、经历和职业规划展开
                2. 如果回答过于简短（如"就读南京理工大学"），应温和地引导候选人展开更多背景信息
                3. 可以追问：专业方向、学习经历、项目经历概览、职业发展目标等
                4. 语气亲切友好，像一个HR在了解候选人的基本情况
                5. 不要追问技术细节，不要出技术题
                6. 追问控制在 100 字以内
                """;
            case "qaRound" -> """
                要求：
                1. 这是反问环节，鼓励候选人主动提问
                2. 如果候选人没有提问，可以给一些示例问题引导
                3. 不要追问技术细节
                4. 语气友好开放
                """;
            case "projectDeep" -> """
                要求：
                1. 追问必须基于候选人的实际回答内容，不能是预设模板
                2. 追问要深入到项目细节，考察候选人的真实理解深度
                3. 关注候选人在项目中的角色、架构决策和技术权衡
                4. 如果候选人回答比较浅显，追问应引导其展开更多技术细节
                5. 追问控制在 200 字以内
                6. 保持技术面试官专业、友好的语气
                """;
            default -> // techExam
                """
                要求：
                1. 追问必须基于候选人的实际回答内容，不能是预设模板
                2. 追问要深入到技术细节，考察候选人的真实理解深度
                3. 如果候选人回答已经非常深入，可以适当延伸追问相关领域
                4. 如果候选人回答比较浅显，追问应引导其展开更多细节
                5. 追问控制在 200 字以内
                6. 保持面试官专业、友好的语气
                """;
        };
    }

    private String getStageLabel(String stage) {
        if (stage == null) return "";
        return switch (stage) {
            case "selfIntro" -> "自我介绍";
            case "techExam" -> "技术考察";
            case "projectDeep" -> "项目深挖";
            case "qaRound" -> "反问环节";
            default -> stage;
        };
    }

    private String getFallbackFollowUp(String stage, int followUpIndex) {
        if (stage == null) stage = "";
        return switch (stage) {
            case "selfIntro" -> switch (followUpIndex) {
                case 1 -> "可以再详细介绍一下你的专业背景和学习经历吗？";
                case 2 -> "你在大学期间有没有参与过什么项目或实践活动？";
                default -> "请继续介绍一下你的其他经历。";
            };
            case "qaRound" -> "如果你暂时想不到问题，也可以聊聊你对这个岗位的期望。";
            default -> switch (followUpIndex) {
                case 1 -> "能否进一步深入谈谈你在技术选型时做的权衡和取舍？";
                case 2 -> "你提到了具体的实施方案，那在面对资源限制或时间压力时，你是如何做优先级决策的？";
                case 3 -> "这个方案在极端场景下（如流量暴增 10 倍）还能保持稳定吗？你考虑过哪些容灾措施？";
                default -> "请继续深入分享一下你在这个技术领域的其他实践经验。";
            };
        };
    }

    /**
     * 判断是否需要继续追问
     */
    public boolean shouldContinueFollowUp(int followUpCount, int currentIndex) {
        return currentIndex < followUpCount;
    }

    /**
     * 生成过渡到下题的面试官点评
     */
    public String generateTransition(String lastAnswer, InterviewQuestion question,
                                      InterviewQuestion nextQuestion, String stage, int questionNumber) {
        try {
            String prompt = buildTransitionPrompt(lastAnswer, question, nextQuestion, stage, questionNumber);
            return callAiWithRetry(prompt, "过渡语");
        } catch (Exception e) {
            log.warn("生成过渡点评失败，使用fallback: {}", e.getMessage());
            return getFallbackTransition(nextQuestion, stage, questionNumber);
        }
    }

    private String buildTransitionPrompt(String lastAnswer, InterviewQuestion question,
                                          InterviewQuestion nextQuestion, String stage, int questionNumber) {
        String stageInstruction = getStageTransitionInstruction(stage);
        return String.format("""
                你是一个面试官，需要根据候选人的回答进行简要点评，然后自然地引出下一道题。
                
                当前面试阶段：%s
                原始问题：%s
                候选人回答：%s
                下一道题目（第%d题）：%s
                
                %s
                
                请给出面试官的发言，开头自然地标注"第%d题："：
                """, getStageLabel(stage), question.getText(), lastAnswer, questionNumber, nextQuestion.getText(), stageInstruction, questionNumber);
    }

    private String getStageTransitionInstruction(String stage) {
        if (stage == null) stage = "";
        return switch (stage) {
            case "selfIntro" -> """
                要求：
                1. 这是自我介绍环节，直接引出下一道问题，不要评价回答质量
                2. 不要使用"感谢""非常好""回答简短"等任何评价性语言
                3. 直接自然过渡到下一题
                4. 整个回复控制在 30 字以内
                """;
            case "qaRound" -> """
                要求：
                1. 这是反问环节，鼓励候选人继续提问
                2. 不要评价候选人的提问质量
                3. 语气友好开放
                """;
            default -> """
                要求：
                1. 直接进入下一题，不要做任何评价
                2. 不要使用"感谢""回答得好""回答简要"等评价性语言
                3. 直接激发出下一道题
                4. 控制在 50 字以内
                """;
        };
    }

    private String getFallbackTransition(InterviewQuestion nextQuestion, String stage, int questionNumber) {
        if (stage == null) stage = "";
        String prefix = "第" + questionNumber + "题：";
        return switch (stage) {
            case "selfIntro" -> prefix + nextQuestion.getText();
            case "qaRound" -> "好的。如果你还有其他问题，可以继续问。" + prefix + nextQuestion.getText();
            default -> prefix + "\n\n" + nextQuestion.getText();
        };
    }

    /**
     * 反问环节：使用AI回答候选人提出的问题
     */
    public String generateQaAnswer(String candidateMessage, InterviewQuestion currentQuestion) {
        try {
            String prompt = buildQaAnswerPrompt(candidateMessage, currentQuestion);
            return callAiWithRetry(prompt, "反问回答");
        } catch (Exception e) {
            log.warn("生成反问回答失败，使用fallback: {}", e.getMessage());
            return "好的。如果你还有其他问题，可以继续问。";
        }
    }

    private String buildQaAnswerPrompt(String candidateMessage, InterviewQuestion currentQuestion) {
        return String.format("""
                你是一个面试官，正在面试的反问环节。
                
                候选人说：%s
                
                当前环节的引导提示：%s
                
                要求：
                1. 如果候选人提出了具体问题（关于团队、技术、公司等），请以面试官身份认真回答
                2. 回答要专业、真诚，给出有信息量的回复
                3. 回答完毕后，鼓励候选人继续提问："你还有其他想了解的吗？"
                4. 如果候选人表示没有问题了（如"没有了""暂时没有"等），请回答："好的，那反问环节就到这里。系统正在为你生成评估报告..."
                5. 如果候选人只是简单回应（如"好的"），请给出引导性问题示例帮助其提问
                6. 整个回复控制在 200 字以内
                
                请给出面试官的发言：
                """, candidateMessage, currentQuestion.getText());
    }
}