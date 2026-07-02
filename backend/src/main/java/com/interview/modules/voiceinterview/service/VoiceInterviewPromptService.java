package com.interview.modules.voiceinterview.service;

import com.interview.common.ai.PromptSanitizer;
import com.interview.common.ai.PromptSecurityConstants;
import com.interview.modules.voiceinterview.config.VoiceInterviewProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoiceInterviewPromptService {

    private final PromptSanitizer promptSanitizer;
    private final VoiceInterviewProperties properties;

    public VoiceInterviewPromptService(PromptSanitizer promptSanitizer, VoiceInterviewProperties properties) {
        this.promptSanitizer = promptSanitizer;
        this.properties = properties;
    }

    private static final String FOUR_PHASE_FLOW_INSTRUCTION = """
            【面试流程要求 - 结构化四阶段】
            本场面试严格分为四个阶段，请按顺序推进：
            1. 第一阶段：自我介绍 → 请候选人介绍技术背景和项目经历
            2. 第二阶段：技术考察 → 围绕目标方向出 %d-%d 道技术问题，逐步深入
            3. 第三阶段：项目深挖 → 针对候选人提到的项目做深入追问（最多 %d 次深度追问）
            4. 第四阶段：反问环节 → 邀请候选人提问，解答候选人疑问
            当前你处于第 %d 阶段，请严格遵循阶段要求提问。一个阶段完成后再进入下一个阶段。

            【深度追问规则】
            - 如果候选人回答含糊、不完整，必须追问具体细节，直到说清楚
            - 如果回答正确，可以追问更深入的问题，考察理解深度
            - 每个问题最多 %d 次追问，追问完了再换题
            """;

    private static final String VOICE_RESPONSE_CONSTRAINTS = """
            【语音面试输出约束】
            1. 每轮只问 1 个主问题，必要时最多补 1 个短追问。
            2. 总长度控制在 2-4 句，避免长段落、列表、Markdown、代码块。
            3. 不要重复开场白，不要复述上一轮已问过的完整问题。
            4. 若候选人回答过短或含糊，直接追问一个具体的技术细节或给出提示引导，不要简单确认后停止。
            5. 当候选人明确要求换题时，立即切换到新的技术方向，不要停留在当前话题。
            6. 语气简洁直接，适配口语对话。
            """;

    private static final String SKILL_TOOL_INSTRUCTION = """
            你是一位 %s 方向的面试官。
            如果尚未加载完整的角色设定，请调用 Skill 工具（command: %s）加载该技能的 SKILL.md。
            工具输出包含完整的面试官角色和出题规则，后续对话应基于该角色进行。
            """;

    public String generateSystemPromptWithContext(String skillId, String resumeText,
                                                   String currentPhase, int askedQuestions, int currentFollowUp) {
        StringBuilder prompt = new StringBuilder();

        if (skillId != null && !skillId.isBlank()) {
            prompt.append(String.format(SKILL_TOOL_INSTRUCTION, skillId, skillId));
        }

        // 如果启用四阶段流程，添加结构化提示
        if (properties.isEnableFourPhaseFlow()) {
            VoiceInterviewProperties.DurationConfig phaseConfig = getPhaseConfig(currentPhase);
            int phaseNumber = getPhaseNumber(currentPhase);
            int maxFollowUp = phaseConfig.getMaxQuestions();
            prompt.append("\n\n").append(String.format(FOUR_PHASE_FLOW_INSTRUCTION,
                phaseConfig.getMinQuestions(),
                phaseConfig.getMaxQuestions(),
                maxFollowUp,
                phaseNumber,
                currentFollowUp + 1
            ));
        }

        prompt.append("\n\n").append(VOICE_RESPONSE_CONSTRAINTS);

        if (resumeText != null && !resumeText.isEmpty()) {
            String safeResume = promptSanitizer.sanitize(resumeText);
            prompt.append("\n\n【实时语音面试 - 候选人简历内容】\n");
            if (askedQuestions == 0) {
                // 首轮：说明已查阅简历，开始提问
                prompt.append("你已查阅过候选人简历。首轮仅用一句话说明已查阅，并立即进入首个问题。\n\n");
            } else {
                // 非首轮：根据对话历史继续推进，不要重复开场白和已问过的问题
                prompt.append("你已查阅过候选人简历，当前是第")
                    .append(askedQuestions + 1)
                    .append("轮对话。根据【之前的对话】继续推进面试，不要重复开场白，不要复述已问过的问题。\n\n");
            }
            prompt.append("【简历解析文本】\n")
                .append(promptSanitizer.wrapWithDelimiters("resume", safeResume));
        }

        prompt.append(PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION);
        return prompt.toString();
    }

    /** 获取当前阶段的配置 */
    private VoiceInterviewProperties.DurationConfig getPhaseConfig(String phaseName) {
        return switch (phaseName) {
            case "INTRO" -> properties.getPhase().getIntro();
            case "TECH" -> properties.getPhase().getTech();
            case "PROJECT" -> properties.getPhase().getProject();
            case "HR" -> properties.getPhase().getHr();
            default -> properties.getPhase().getTech();
        };
    }

    /** 阶段序号，用于提示词 */
    private int getPhaseNumber(String phaseName) {
        return switch (phaseName) {
            case "INTRO" -> 1;
            case "TECH" -> 2;
            case "PROJECT" -> 3;
            case "HR" -> 4;
            default -> 1;
        };
    }

    /** 兼容旧调用 */
    public String generateSystemPromptWithContext(String skillId, String resumeText) {
        return generateSystemPromptWithContext(skillId, resumeText, "INTRO", 0, 0);
    }
}