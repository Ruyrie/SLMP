package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.request.MultipartCompleteRequest;
import com.zhimu.server.admin.content.request.MultipartInitRequest;
import com.zhimu.server.admin.content.service.AdminFileService;
import com.zhimu.server.admin.content.vo.AdminFileVO;
import com.zhimu.server.admin.content.vo.FileDownloadVO;
import com.zhimu.server.admin.content.vo.MultipartInitVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端媒体：ADM-S34–S39。
 * 场景级权限（WA-C01 / C03 / C04）由 Service 再校验一次；这里只保证至少拥有一种内容运营权限。
 */
@RestController
@RequestMapping("/admin/v1/files")
@RequiredArgsConstructor
public class FileAdminController {

    private final AdminFileService fileService;

    /** ADM-S34 小文件上传（标题 / 封面 / 知识图片） */
    @PostMapping
    @Idempotent(required = true)
    @AdminPermission({"WA-C01:HANDLE", "WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<AdminFileVO> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam("scene") String scene) {
        return new ApiResponse<>(0, "上传成功", fileService.uploadImage(file, scene));
    }

    /** ADM-S35 分片上传初始化 / 续传 */
    @PostMapping("/multipart")
    @Idempotent
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<MultipartInitVO> initMultipart(@Valid @RequestBody MultipartInitRequest request) {
        return new ApiResponse<>(0, "已创建上传任务", fileService.initMultipart(request));
    }

    /** ADM-S36 合并分片并进入转码 */
    @PostMapping("/{id:\\d+}/multipart/complete")
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<AdminFileVO> completeMultipart(@PathVariable("id") Long id,
                                                      @Valid @RequestBody MultipartCompleteRequest request) {
        return new ApiResponse<>(0, "上传完成，已开始转码", fileService.completeMultipart(id, request));
    }

    /**
     * ADM-S36 服务端代传分片（补口扩展）：MinIO 未配置 CORS 时前端逐片 POST 到应用服务器，
     * 由服务端写入同一个分片上传任务；返回的 ETag 参与合并校验。
     */
    @PostMapping("/{id:\\d+}/multipart/part")
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<MultipartInitVO.UploadedPartVO> uploadPart(@PathVariable("id") Long id,
                                                                  @RequestParam("part_no") Integer partNo,
                                                                  @RequestParam("file") MultipartFile file) {
        return new ApiResponse<>(0, "分片已上传", fileService.uploadPart(id, partNo, file));
    }

    /** ADM-S37 文件状态与转码进度 */
    @GetMapping("/{id:\\d+}")
    @AdminPermission({"WA-C01:VIEW", "WA-C03:VIEW", "WA-C04:VIEW"})
    public ApiResponse<AdminFileVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(fileService.get(id));
    }

    /** ADM-S38 重新转码 */
    @PostMapping("/{id:\\d+}/transcode")
    @AdminPermission({"WA-C03:HANDLE", "WA-C04:HANDLE"})
    public ApiResponse<AdminFileVO> retryTranscode(@PathVariable("id") Long id) {
        return new ApiResponse<>(0, "已重新开始转码", fileService.retryTranscode(id));
    }

    /** ADM-S39 获取下载地址（导出文件 10 分钟有效） */
    @GetMapping("/{id:\\d+}/download")
    @AdminPermission({"WA-C01:VIEW", "WA-C03:VIEW", "WA-C04:VIEW"})
    public ApiResponse<FileDownloadVO> download(@PathVariable("id") Long id) {
        return ApiResponse.ok(fileService.download(id));
    }
}
