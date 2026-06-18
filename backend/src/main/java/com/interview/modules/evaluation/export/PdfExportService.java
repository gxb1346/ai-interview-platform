package com.interview.modules.evaluation.export;

import com.interview.modules.evaluation.model.EvaluationReport;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * PDF 报告导出服务
 * 面试完成后自动异步生成 PDF 评估报告
 */
@Service
public class PdfExportService {

    @Value("${app.pdf.export-path:./reports}")
    private String exportPath;

    private static final String FONT_PATH = "META-INF/resources/webjars/font-asian/8.0.5/NotoSansSC-Regular.otf";
    private static final String[] FONT_FALLBACK_PATHS = {
        // Windows 10/11 中文字体（TTC集合字体，需指定索引0）
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simsun.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/simkai.ttf",
        "C:/Windows/Fonts/simfang.ttf",
        "C:/Windows/Fonts/msyhbd.ttc",
        "C:/Windows/Fonts/msyhl.ttc",
        "C:/Windows/Fonts/simsunb.ttf",
        "C:/Windows/Fonts/SimsunExtG.ttf",
        "C:/Windows/Fonts/SIMYOU.TTF",
        "C:/Windows/Fonts/SIMLI.TTF",
        // 其他备选路径
        "C:/Windows/Fonts/yahei.ttf",
        "C:/Windows/Fonts/msyh.ttf",
        "C:/Windows/Fonts/SimSun.ttf"
    };

    private PdfFont loadChineseFont() {
        // 1. 尝试从 font-asian 库加载
        try {
            return PdfFontFactory.createFont(FONT_PATH, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            System.err.println("font-asian 字体加载失败: " + e.getMessage());
        }
        // 2. 尝试使用系统字体（TTC集合字体指定索引0，TTF直接加载）
        for (String path : FONT_FALLBACK_PATHS) {
            try {
                if (new File(path).exists()) {
                    String fontName = path.toLowerCase();
                    if (fontName.endsWith(".ttc")) {
                        // TTC集合字体：需要指定索引，msyh.ttc的索引0为微软雅黑
                        return PdfFontFactory.createFont(path + ",0", PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
                    } else {
                        return PdfFontFactory.createFont(path, PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
                    }
                }
            } catch (Exception e2) {
                System.err.println("系统字体加载失败: " + path + " - " + e2.getMessage());
            }
        }
        System.err.println("所有系统字体加载失败: 未找到可用的中文字体");
        return null;
    }
    
    private void addParagraph(Document doc, String text, PdfFont font, float fontSize, boolean bold) {
        Paragraph p = new Paragraph(text).setFontSize(fontSize);
        if (font != null) p.setFont(font);
        if (bold) p.setBold();
        doc.add(p);
    }

    private void addTableCell(Table table, String text, PdfFont font) {
        Paragraph p = new Paragraph(text);
        if (font != null) p.setFont(font);
        table.addCell(p);
    }

    @Async
    public CompletableFuture<String> exportReport(EvaluationReport report) {
        try {
            Path dirPath = Paths.get(exportPath);
            Files.createDirectories(dirPath);

            String fileName = String.format("interview_report_%s_%s.pdf",
                    report.getCandidateName().replaceAll("\\s+", "_"),
                    report.getEvaluatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            String filePath = dirPath.resolve(fileName).toString();

            generatePdf(report, filePath);
            return CompletableFuture.completedFuture(filePath);
        } catch (Exception e) {
            System.err.println("PDF 导出失败: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private void generatePdf(EvaluationReport report, String filePath) throws IOException {
        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            PdfFont font = loadChineseFont();
            System.out.println("PDF 字体加载" + (font != null ? "成功" : "失败，将使用默认字体"));

            // 标题
            addParagraph(document, "模拟面试评估报告", font, 22, true);

            // 基本信息
            addParagraph(document, "基本信息", font, 16, true);
            addParagraph(document, "候选人: " + report.getCandidateName(), font, 12, false);
            addParagraph(document, "面试方向: " + report.getDirection(), font, 12, false);
            addParagraph(document, "面试难度: " + report.getLevel(), font, 12, false);
            addParagraph(document, "面试模式: " + report.getMode(), font, 12, false);
            addParagraph(document, "对话轮次: " + report.getTotalRounds() + " 轮", font, 12, false);
            addParagraph(document, "评估时间: " +
                    report.getEvaluatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), font, 12, false);

            // 综合评分
            addParagraph(document, "\n综合评分: " + report.getOverallScore() + "/100", font, 16, true);

            // 维度评分表
            if (report.getDimensionScores() != null && !report.getDimensionScores().isEmpty()) {
                addParagraph(document, "维度评分", font, 14, true);
                Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1}));
                table.setWidth(UnitValue.createPercentValue(100));

                for (var entry : report.getDimensionScores().entrySet()) {
                    String dimName = switch (entry.getKey()) {
                        case "technical" -> "技术深度";
                        case "communication" -> "沟通表达";
                        case "problemSolving" -> "问题解决";
                        case "culturalFit" -> "综合素质";
                        default -> entry.getKey();
                    };
                    addTableCell(table, dimName, font);
                    addTableCell(table, entry.getValue() + "/10", font);
                }
                document.add(table);
            }

            // 最终建议
            addParagraph(document, "\n最终建议: " + report.getVerdict(), font, 14, true);

            // 评估总结
            addParagraph(document, "\nAI 评估总结", font, 14, true);
            addParagraph(document, report.getSummary(), font, 11, false);

            // 优势
            if (report.getStrengths() != null && !report.getStrengths().isEmpty()) {
                addParagraph(document, "\n核心优势", font, 14, true);
                for (String s : report.getStrengths()) {
                    addParagraph(document, "• " + s, font, 11, false);
                }
            }

            // 待改进
            if (report.getImprovements() != null && !report.getImprovements().isEmpty()) {
                addParagraph(document, "\n待改进项", font, 14, true);
                for (String i : report.getImprovements()) {
                    addParagraph(document, "• " + i, font, 11, false);
                }
            }

            // 页脚
            addParagraph(document, "\n\n—— 本报告由 RecruitAI 智能面试系统自动生成 ——", font, 9, false);
        }
    }
}
