package com.interview.modules.resume.controller;

import com.interview.common.result.Result;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.service.ResumeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历分析 REST 控制器
 *
 * 完整处理链路：
 * 用户上传 PDF/DOCX/TXT → Tika 解析文本 → Spring AI 分析 → 存储至 PostgreSQL + MinIO
 */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * 上传并分析简历
     *
     * @param file      简历文件 (PDF / DOCX / TXT)
     * @param targetJob 目标岗位 (可选)
     * @return 结构化分析结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeVO> uploadAndAnalyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetJob", required = false) String targetJob) {

        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        try {
            ResumeVO vo = resumeService.processResume(file, targetJob);
            return Result.success(vo);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("简历分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有简历分析记录
     */
    @GetMapping("/list")
    public Result<List<ResumeVO>> listAll() {
        return Result.success(resumeService.getAllResumes());
    }

    /**
     * 根据 ID 获取简历详情
     */
    @GetMapping("/{id}")
    public Result<ResumeVO> getById(@PathVariable Long id) {
        try {
            return Result.success(resumeService.getResumeById(id));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
