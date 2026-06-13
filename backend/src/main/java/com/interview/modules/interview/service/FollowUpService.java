package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 智能追问服务
 * 支持配置多轮智能追问（默认 1 条），模拟多轮问答场景
 */
@Service
public class FollowUpService {

    private final ChatClient chatClient;

    public FollowUpService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个技术面试官，善于根据候选人的回答进行深度追问。")
                .build();
    }

    /**
     * 生成智能追问
     *
     * @param lastAnswer    候选人的上一条回答
     * @param question      当前面试题目
     * @param followUpIndex 当前是第几轮追问（从 1 开始）
     * @return 追问内容
     */
    public String generateFollowUp(String lastAnswer, InterviewQuestion question, int followUpIndex) {
        try {
            String prompt = buildFollowUpPrompt(lastAnswer, question, followUpIndex);
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("生成追问失败: " + e.getMessage());
            return getFallbackFollowUp(question, followUpIndex);
        }
    }

    private String buildFollowUpPrompt(String lastAnswer, InterviewQuestion question, int followUpIndex) {
        return String.format("""
                你是一个技术面试官，正在对候选人进行深度追问。
                
                本轮是第 %d 轮追问。
                原始问题：%s
                候选人回答：%s
                
                要求：
                1. 追问必须基于候选人的实际回答内容，不能是预设模板
                2. 追问要深入到技术细节，考察候选人的真实理解深度
                3. 如果候选人回答已经非常深入，可以适当延伸追问相关领域
                4. 如果候选人回答比较浅显，追问应引导其展开更多细节
                5. 追问控制在 200 字以内
                6. 保持面试官专业、友好的语气
                
                请给出追问内容：
                """, followUpIndex, question.getText(), lastAnswer);
    }

    private String getFallbackFollowUp(InterviewQuestion question, int followUpIndex) {
        return switch (followUpIndex) {
            case 1 -> "能否进一步深入谈谈你在技术选型时做的权衡和取舍？";
            case 2 -> "你提到了具体的实施方案，那在面对资源限制或时间压力时，你是如何做优先级决策的？";
            case 3 -> "这个方案在极端场景下（如流量暴增 10 倍）还能保持稳定吗？你考虑过哪些容灾措施？";
            default -> "请继续深入分享一下你在这个技术领域的其他实践经验。";
        };
    }

    /**
     * 判断是否需要继续追问
     *
     * @param followUpCount 配置的追问次数
     * @param currentIndex  当前追问轮次
     * @return 是否继续
     */
    public boolean shouldContinueFollowUp(int followUpCount, int currentIndex) {
        return currentIndex < followUpCount;
    }

    /**
     * 生成过渡到下题的面试官点评
     * 根据候选人的回答质量，动态生成恰当的过渡语
     *
     * @param lastAnswer 候选人的上一条回答
     * @param question   当前面试题目
     * @param nextQuestion 下一道面试题目
     * @return 过渡点评 + 下一题
     */
    public String generateTransition(String lastAnswer, InterviewQuestion question, InterviewQuestion nextQuestion) {
        try {
            String prompt = buildTransitionPrompt(lastAnswer, question, nextQuestion);
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("生成过渡点评失败: " + e.getMessage());
            return getFallbackTransition(nextQuestion);
        }
    }

    private String buildTransitionPrompt(String lastAnswer, InterviewQuestion question, InterviewQuestion nextQuestion) {
        return String.format("""
                你是一个资深技术面试官，需要根据候选人的回答进行简要点评，然后自然地引出下一道面试题。

                原始问题：%s
                候选人回答：%s
                下一道题目：%s

                要求：
                1. 先对候选人的回答给出简短、客观的评价（10-30字），好的回答就肯定，敷衍的回答要委婉指出
                2. 然后自然过渡到下一道题目，不要使用“非常好”等过于夸张的表扬
                3. 如果回答极其简短（如少于10个字），应委婉表示希望听到更详细的内容
                4. 整个回复控制在 80 字以内，简洁自然
                5. 语气专业、友好，像一个真实的面试官

                请给出面试官的发言：
                """, question.getText(), lastAnswer, nextQuestion.getText());
    }

    private String getFallbackTransition(InterviewQuestion nextQuestion) {
        return "感谢你的回答。接下来我们进入下一题：\n\n" + nextQuestion.getText();
    }
}
