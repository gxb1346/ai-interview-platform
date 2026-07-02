package com.interview.modules.resume.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.modules.resume.model.ResumeUpdateDTO;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.service.ResumeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
     * 批量上传并分析多份简历（最多20份）
     */
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<Map<String, Object>>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "targetJob", required = false) String targetJob) {

        if (files == null || files.isEmpty()) {
            return Result.error("上传文件列表为空");
        }

        if (files.size() > 20) {
            return Result.error("单次上传不能超过 20 份简历");
        }

        try {
            List<Map<String, Object>> results = resumeService.batchProcessResumes(files, targetJob);
            return Result.success(results);
        } catch (Exception e) {
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }



    /**
     * 异步上传简历（通过 Redis Stream 异步分析）
     * 返回 taskId，前端可通过 taskId 查询分析进度
     */
    @PostMapping(value = "/upload-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetJob", required = false) String targetJob) {

        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        try {
            String taskId = resumeService.uploadResumeAsync(file, targetJob);
            if ("duplicate".equals(taskId)) {
                return Result.error("该简历内容已存在，请勿重复上传");
            }
            return Result.success(Map.of("taskId", taskId, "status", "PENDING"));
        } catch (Exception e) {
            return Result.error("简历上传失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询简历列表（支持搜索、筛选）
     */
    @GetMapping("/page")
    public Result<PageResult<ResumeVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore) {
        PageResult<ResumeVO> result = resumeService.getResumePage(keyword, education, minScore, maxScore, page, pageSize);
        return Result.success(result);
    }

    /**
     * 获取所有简历分析记录
     */
    @GetMapping("/list")
    public Result<List<ResumeVO>> listAll() {
        return Result.success(resumeService.getAllResumes());
    }

    /**
     * 获取人才库候选人列表
     */
    @GetMapping("/talent-pool")
    public Result<List<ResumeVO>> getTalentPool() {
        return Result.success(resumeService.getTalentPool());
    }

    /**
     * 移入人才库
     */
    @PostMapping("/{id}/to-talent-pool")
    public Result<ResumeVO> moveToTalentPool(@PathVariable Long id) {
        try {
            return Result.success(resumeService.moveToTalentPool(id));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新人才库状态
     */
    @PutMapping("/{id}/talent-status")
    public Result<ResumeVO> updateTalentStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String status = body.get("talentStatus");
            return Result.success(resumeService.updateTalentStatus(id, status));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
    /**
     * 从人才库移除
     */
    @DeleteMapping("/{id}/remove-from-talent-pool")
    public Result<ResumeVO> removeFromTalentPool(@PathVariable Long id) {
        try {
            return Result.success(resumeService.removeFromTalentPool(id));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
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

    /**
     * 手动修正 AI 解析结果
     */
    @PutMapping("/{id}")
    public Result<ResumeVO> update(@PathVariable Long id, @RequestBody ResumeUpdateDTO dto) {
        try {
            ResumeVO vo = resumeService.updateResume(id, dto);
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 批量软删除简历
     */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("删除ID列表为空");
        }
        try {
            resumeService.batchSoftDelete(ids);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 软删除简历
     */
    @DeleteMapping("/{id}/soft")
    public Result<Void> softDelete(@PathVariable Long id) {
        try {
            resumeService.softDelete(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 硬删除简历
     */
    @DeleteMapping("/{id}/hard")
    public Result<Void> hardDelete(@PathVariable Long id) {
        try {
            resumeService.hardDelete(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}