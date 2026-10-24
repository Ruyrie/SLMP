package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.request.MultipartCompleteRequest;
import com.zhimu.server.admin.content.request.MultipartInitRequest;
import com.zhimu.server.admin.content.vo.AdminFileVO;
import com.zhimu.server.admin.content.vo.FileDownloadVO;
import com.zhimu.server.admin.content.vo.MultipartInitVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端文件（ADM-S34–S39）：小文件上传、视频分片直传、合并转码、状态、重试与下载。
 * 权限按场景落到 WA-C01 / C03 / C04 的 VIEW / HANDLE。
 */
public interface AdminFileService {

    AdminFileVO uploadImage(MultipartFile file, String scene);

    MultipartInitVO initMultipart(MultipartInitRequest request);

    AdminFileVO completeMultipart(Long id, MultipartCompleteRequest request);

    /** 服务端代传分片（MinIO 未配置 CORS 时使用，避免浏览器直传失败） */
    MultipartInitVO.UploadedPartVO uploadPart(Long id, Integer partNo, MultipartFile file);

    AdminFileVO get(Long id);

    AdminFileVO retryTranscode(Long id);

    FileDownloadVO download(Long id);
}
