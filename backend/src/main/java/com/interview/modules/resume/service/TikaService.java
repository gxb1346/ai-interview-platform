package com.interview.modules.resume.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Apache Tika 文档解析服务
 * 支持解析: PDF, DOCX, TXT, HTML, ODT, RTF 等数十种格式
 */
@Service
public class TikaService {

    private final Tika tika = new Tika();

    /**
     * 从上传文件中提取纯文本内容
     *
     * @param file 上传的文件 (MultipartFile)
     * @return 提取的纯文本
     */
    public String extractText(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            fileName = "unknown";
        }

        try (InputStream inputStream = file.getInputStream()) {
            // Tika 自动检测文件类型并调用对应的 Parser
            // PDF → PDFParser, DOCX → OOXMLParser, TXT → TXTParser
            return tika.parseToString(inputStream);
        } catch (IOException | TikaException e) {
            throw new RuntimeException("文档解析失败: " + fileName + " - " + e.getMessage(), e);
        }
    }

    /**
     * 从 InputStream 中提取纯文本
     */
    public String extractText(InputStream inputStream, String fileName) {
        try {
            return tika.parseToString(inputStream);
        } catch (IOException | TikaException e) {
            throw new RuntimeException("文档解析失败: " + fileName + " - " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件类型 (小写)
     */
    public static String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
