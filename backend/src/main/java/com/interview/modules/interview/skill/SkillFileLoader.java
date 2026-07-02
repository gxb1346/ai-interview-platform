package com.interview.modules.interview.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill 文件加载器
 * 从 resources/skills/{dirName}/ 加载 SKILL.md 和 skill.meta.yml
 * 从 resources/skills/_shared/references/ 加载共享知识库
 */
@Slf4j
public class SkillFileLoader {

    private static final String SKILLS_BASE = "skills/";
    private static final String SHARED_REFERENCES = "skills/_shared/references/";

    /** 中文方向名 → 目录名的映射 */
    private static final Map<String, String> DIRECTION_TO_DIR = Map.ofEntries(
            Map.entry("Java后端开发", "java-backend"),
            Map.entry("AI Agent开发", "ai-agent-dev"),
            Map.entry("阿里后端", "ali-backend"),
            Map.entry("字节后端", "bytedance-backend"),
            Map.entry("腾讯后端", "java-backend-tencent"),
            Map.entry("前端工程", "frontend"),
            Map.entry("Python后端开发", "python-backend"),
            Map.entry("算法与数据结构", "algorithm"),
            Map.entry("系统设计", "system-design"),
            Map.entry("测试开发", "test-development")
    );

    /**
     * 加载指定方向的 Skill 数据
     */
    public static SkillDefinition load(String directionName) {
        String dirName = DIRECTION_TO_DIR.get(directionName);
        if (dirName == null) {
            log.warn("未找到方向 {} 的目录映射，使用默认实现", directionName);
            return null;
        }

        String skillPath = SKILLS_BASE + dirName + "/SKILL.md";
        String metaPath = SKILLS_BASE + dirName + "/skill.meta.yml";

        String skillContent = readClasspathFile(skillPath);
        if (skillContent == null) {
            log.warn("SKILL.md 不存在: {}", skillPath);
            return null;
        }

        String metaContent = readClasspathFile(metaPath);
        if (metaContent == null) {
            log.warn("skill.meta.yml 不存在: {}", metaPath);
            return null;
        }

        // 解析 SKILL.md 的 front matter
        ParsedSkillMd parsed = parseSkillMd(skillContent);

        // 解析 skill.meta.yml
        MetaYaml meta = parseMetaYaml(metaContent);

        // 加载共享知识库
        String knowledgeBase = loadSharedReferences(meta.categories);

        return SkillDefinition.builder()
                .directionName(directionName)
                .dirName(dirName)
                .name(parsed.name != null ? parsed.name : meta.displayName)
                .description(parsed.description != null ? parsed.description : meta.displayName)
                .displayName(meta.displayName)
                .icon(meta.icon)
                .gradient(meta.gradient)
                .iconBg(meta.iconBg)
                .iconColor(meta.iconColor)
                .systemPrompt(parsed.body)
                .scopeAreas(meta.buildScopeAreas())
                .knowledgeBase(knowledgeBase)
                .version("2.0.0")
                .build();
    }

    /**
     * 加载共享知识库
     */
    private static String loadSharedReferences(List<MetaCategory> categories) {
        Set<String> loadedRefs = new HashSet<>();
        StringBuilder knowledge = new StringBuilder();

        for (MetaCategory cat : categories) {
            if (cat.ref != null && cat.shared && !loadedRefs.contains(cat.ref)) {
                loadedRefs.add(cat.ref);
                String refPath = SHARED_REFERENCES + cat.ref;
                String content = readClasspathFile(refPath);
                if (content != null) {
                    knowledge.append("## ").append(cat.label).append("\n");
                    // 取前 3000 字符作为参考，避免 prompt 过长
                    String trimmed = content.length() > 3000 ? content.substring(0, 3000) + "\n...(已截断)" : content;
                    knowledge.append(trimmed).append("\n\n");
                }
            }
        }
        return knowledge.toString();
    }

    /**
     * 解析 SKILL.md 的 YAML front matter 和 Markdown body
     */
    private static ParsedSkillMd parseSkillMd(String content) {
        ParsedSkillMd result = new ParsedSkillMd();

        // 匹配 YAML front matter: --- ... ---
        Pattern frontMatterPattern = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);
        Matcher matcher = frontMatterPattern.matcher(content);

        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            result.body = matcher.group(2).trim();

            // 解析 name 和 description
            for (String line : frontMatter.split("\n")) {
                line = line.trim();
                if (line.startsWith("name:")) {
                    result.name = line.substring(5).trim();
                } else if (line.startsWith("description:")) {
                    result.description = line.substring(12).trim();
                }
            }
        } else {
            // 没有 front matter，整个文件作为 body
            result.body = content.trim();
        }
        return result;
    }

    /**
     * 解析 skill.meta.yml
     * 使用简易 YAML 解析器（避免引入额外依赖）
     */
    private static MetaYaml parseMetaYaml(String content) {
        MetaYaml meta = new MetaYaml();
        List<MetaCategory> categories = new ArrayList<>();
        MetaCategory currentCat = null;
        boolean inCategories = false;

        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("displayName:")) {
                meta.displayName = unquote(line.substring(12).trim());
            } else if (line.startsWith("display:")) {
                // 跳过 display: 父节点，子节点在下面处理
            } else if (line.startsWith("icon:") && rawLine.contains("display:")) {
                String iconLine = line.substring(5).trim();
                if (iconLine.startsWith("icon:")) iconLine = iconLine.substring(5).trim();
                meta.icon = unquote(iconLine);
            } else if (line.startsWith("icon:")) {
                meta.icon = unquote(line.substring(5).trim());
            } else if (line.startsWith("gradient:")) {
                meta.gradient = unquote(line.substring(9).trim());
            } else if (line.startsWith("iconBg:")) {
                meta.iconBg = unquote(line.substring(7).trim());
            } else if (line.startsWith("iconColor:")) {
                meta.iconColor = unquote(line.substring(10).trim());
            } else if (line.startsWith("categories:")) {
                inCategories = true;
            } else if (inCategories && line.startsWith("- key:")) {
                if (currentCat != null) categories.add(currentCat);
                currentCat = new MetaCategory();
                currentCat.key = unquote(line.substring(5).trim());
            } else if (inCategories && currentCat != null && line.startsWith("label:")) {
                currentCat.label = unquote(line.substring(6).trim());
            } else if (inCategories && currentCat != null && line.startsWith("priority:")) {
                currentCat.priority = unquote(line.substring(9).trim());
            } else if (inCategories && currentCat != null && line.startsWith("ref:")) {
                currentCat.ref = unquote(line.substring(4).trim());
            } else if (inCategories && currentCat != null && line.startsWith("shared:")) {
                currentCat.shared = "true".equals(line.substring(7).trim());
            } else if (inCategories && line.startsWith("- ") && !line.startsWith("- key:")) {
                // 列表项但不是 key，忽略
            }
        }
        if (currentCat != null) categories.add(currentCat);

        meta.categories = categories;
        return meta;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 读取 classpath 文件
     */
    private static String readClasspathFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) return null;
            try (InputStream is = resource.getInputStream()) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("读取文件失败: {} - {}", path, e.getMessage());
            return null;
        }
    }

    // ---- 内部数据类 ----

    /** 解析后的 SKILL.md */
    static class ParsedSkillMd {
        String name;
        String description;
        String body;
    }

    /** 解析后的 skill.meta.yml */
    static class MetaYaml {
        String displayName;
        String icon = "📋";
        String gradient = "from-blue-500 to-indigo-500";
        String iconBg = "bg-blue-100";
        String iconColor = "text-blue-600";
        List<MetaCategory> categories = new ArrayList<>();

        List<String> buildScopeAreas() {
            return categories.stream()
                    .map(c -> c.label)
                    .collect(Collectors.toList());
        }
    }

    /** skill.meta.yml 中的 category 条目 */
    static class MetaCategory {
        String key;
        String label;
        String priority;
        String ref;
        boolean shared;
    }
}