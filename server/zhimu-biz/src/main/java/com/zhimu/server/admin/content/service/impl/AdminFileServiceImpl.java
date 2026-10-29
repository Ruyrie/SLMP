package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.request.MultipartCompleteRequest;
import com.zhimu.server.admin.content.request.MultipartInitRequest;
import com.zhimu.server.admin.content.service.AdminFileService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.support.FileScenePermissions;
import com.zhimu.server.admin.content.vo.AdminFileVO;
import com.zhimu.server.admin.content.vo.CoverCandidateVO;
import com.zhimu.server.admin.content.vo.FileDownloadVO;
import com.zhimu.server.admin.content.vo.MultipartInitVO;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.FileKind;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.enums.VariantCode;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.integration.media.ImageInfo;
import com.zhimu.server.integration.media.ImageProcessor;
import com.zhimu.server.integration.media.MediaInfo;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.storage.MinioProperties;
import com.zhimu.server.integration.storage.ObjectStorage;
import com.zhimu.server.integration.storage.StoredObject;
import com.zhimu.server.integration.storage.UploadedPart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 管理端文件（ADM-S34–S39）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileServiceImpl implements AdminFileService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_VIDEO_MS = 30L * 60 * 1000;
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileMapper fileMapper;
    private final FileVariantMapper variantMapper;
    private final ObjectStorage objectStorage;
    private final MediaProcessor mediaProcessor;
    private final ImageProcessor imageProcessor;
    private final MinioProperties minioProperties;
    private final TranscodeService transcodeService;
    private final AdminPermissionService permissionService;
    private final ContentMediaSupport mediaSupport;
    private final ContentLogs logs;

    @Override
    @Transactional
    public AdminFileVO uploadImage(MultipartFile file, String sceneCode) {
        FileScene scene = FileScenePermissions.scene(sceneCode);
        if (!FileScenePermissions.smallUploadAllowed(scene)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
        requireHandle(scene);
        if (file == null || file.isEmpty()) {
            throw ContentErrors.field("file", "请选择文件");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG 或 PNG 图片，单张不超过 5MB");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            throw ContentErrors.field("file", "文件读取失败，请重新选择");
        }
        ImageInfo info;
        try {
            info = imageProcessor.readInfo(data);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG 或 PNG 图片，单张不超过 5MB");
        }
        String ext = "PNG".equals(info.format()) ? "png" : "jpg";
        // 知识图片放私有桶的 knowledge_media/ 前缀（只开放该前缀匿名只读，用户端原文可直接显示）
        String bucket = scene == FileScene.KNOWLEDGE_MEDIA ? objectStorage.privateBucket()
                : objectStorage.bucketOf(scene);
        String objectKey = objectStorage.buildObjectKey(scene, ext);
        objectStorage.put(bucket, objectKey, new java.io.ByteArrayInputStream(data), data.length, info.mimeType());

        FileEntity entity = new FileEntity();
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setOriginalName(truncate(file.getOriginalFilename(), 255));
        entity.setFileKind(FileKind.IMAGE.name());
        entity.setScene(scene.name());
        entity.setMimeType(info.mimeType());
        entity.setFileExt(ext);
        entity.setFileSize((long) data.length);
        entity.setWidth(info.width());
        entity.setHeight(info.height());
        entity.setUploadStatus(UploadStatus.READY.name());
        entity.setTranscodeStatus(TranscodeStatus.NONE.name());
        entity.setUploaderType("ADMIN");
        entity.setClientType("ADMIN_WEB");
        entity.setBound(0);
        fileMapper.insert(entity);
        writeLog("UPLOAD", entity.getId(), scene, entity.getOriginalName());
        return toVO(entity);
    }

    @Override
    @Transactional
    public MultipartInitVO initMultipart(MultipartInitRequest request) {
        FileScene scene = FileScenePermissions.scene(request.getScene());
        if (!FileScenePermissions.multipartAllowed(scene)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
        requireHandle(scene);
        String ext = extension(request.getOriginalName());
        if (!VIDEO_EXT.contains(ext)) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 MP4 和 MOV，不超过 2GB、30 分钟");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0 || request.getFileSize() > MAX_VIDEO_BYTES) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 MP4 和 MOV，不超过 2GB、30 分钟");
        }
        long partSize = minioProperties.getMultipartPartSize().toBytes();
        int partCount = (int) ((request.getFileSize() + partSize - 1) / partSize);

        FileEntity file;
        if (request.getFileId() != null) {
            file = requireOwnFile(request.getFileId());
            if (!scene.name().equals(file.getScene()) || !UploadStatus.UPLOADING.name().equals(file.getUploadStatus())) {
                throw ContentErrors.stateChanged("文件已处理，请刷新");
            }
            if (file.getMultipartUploadId() == null) {
                throw ContentErrors.stateChanged("上传任务不存在，请重新上传");
            }
            if (!Objects.equals(file.getFileSize(), request.getFileSize())) {
                throw ContentErrors.field("file_size", "文件大小与首次上传不一致");
            }
        } else {
            String objectKey = "raw/" + BeijingTime.today().format(DAY) + "/"
                    + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            String bucket = objectStorage.courseBucket();
            String uploadId = objectStorage.initMultipart(bucket, objectKey, mime(request.getMimeType()));
            file = new FileEntity();
            file.setBucket(bucket);
            file.setObjectKey(objectKey);
            file.setOriginalName(truncate(request.getOriginalName(), 255));
            file.setFileKind(FileKind.VIDEO.name());
            file.setScene(scene.name());
            file.setMimeType(mime(request.getMimeType()));
            file.setFileExt(ext);
            file.setFileSize(request.getFileSize());
            file.setUploadStatus(UploadStatus.UPLOADING.name());
            file.setMultipartUploadId(uploadId);
            file.setTranscodeStatus(TranscodeStatus.NONE.name());
            file.setUploaderType("ADMIN");
            file.setClientType("ADMIN_WEB");
            file.setBound(0);
            fileMapper.insert(file);
            writeLog("UPLOAD_INIT", file.getId(), scene, file.getOriginalName());
        }
        Set<Integer> uploaded = new LinkedHashSet<>();
        MultipartInitVO vo = new MultipartInitVO();
        vo.setFileId(file.getId());
        vo.setPartSize(partSize);
        vo.setPartCount(partCount);
        vo.setOriginalName(file.getOriginalName());
        vo.setUrlExpireAt(BeijingTime.now().plusHours(24));
        if (file.getMultipartUploadId() != null && request.getFileId() != null) {
            for (UploadedPart part : objectStorage.listParts(file.getBucket(), file.getObjectKey(),
                    file.getMultipartUploadId())) {
                MultipartInitVO.UploadedPartVO uploadedVO = new MultipartInitVO.UploadedPartVO();
                uploadedVO.setPartNo(part.partNumber());
                uploadedVO.setEtag(part.etag());
                vo.getUploadedParts().add(uploadedVO);
                uploaded.add(part.partNumber());
            }
        }
        for (int partNo = 1; partNo <= partCount; partNo++) {
            if (uploaded.contains(partNo)) {
                continue;
            }
            MultipartInitVO.PartUrlVO part = new MultipartInitVO.PartUrlVO();
            part.setPartNo(partNo);
            part.setUrl(objectStorage.presignedPartUrl(file.getBucket(), file.getObjectKey(),
                    file.getMultipartUploadId(), partNo, Duration.ofHours(24)));
            vo.getPartUrls().add(part);
        }
        return vo;
    }

    @Override
    @Transactional
    public MultipartInitVO.UploadedPartVO uploadPart(Long id, Integer partNo, MultipartFile file) {
        FileEntity entity = requireOwnFile(id);
        requireHandle(FileScene.valueOf(entity.getScene()));
        if (!UploadStatus.UPLOADING.name().equals(entity.getUploadStatus())) {
            throw ContentErrors.stateChanged("文件已处理，请刷新");
        }
        if (entity.getMultipartUploadId() == null) {
            throw ContentErrors.stateChanged("上传任务不存在，请重新上传");
        }
        long partSize = minioProperties.getMultipartPartSize().toBytes();
        int partCount = (int) ((entity.getFileSize() + partSize - 1) / partSize);
        if (partNo == null || partNo < 1 || partNo > partCount) {
            throw ContentErrors.param("分片编号不正确");
        }
        if (file == null || file.isEmpty()) {
            throw ContentErrors.field("file", "分片为空");
        }
        if (file.getSize() > partSize || (partNo < partCount && file.getSize() != partSize)) {
            throw ContentErrors.param("分片大小不正确");
        }
        try (InputStream in = file.getInputStream()) {
            UploadedPart part = objectStorage.uploadPart(entity.getBucket(), entity.getObjectKey(),
                    entity.getMultipartUploadId(), partNo, in, file.getSize());
            MultipartInitVO.UploadedPartVO vo = new MultipartInitVO.UploadedPartVO();
            vo.setPartNo(part.partNumber());
            vo.setEtag(part.etag());
            return vo;
        } catch (java.io.IOException e) {
            throw ContentErrors.param("分片读取失败，请重试");
        }
    }

    @Override
    public AdminFileVO completeMultipart(Long id, MultipartCompleteRequest request) {
        FileEntity file = requireOwnFile(id);
        requireHandle(FileScene.valueOf(file.getScene()));
        if (!UploadStatus.UPLOADING.name().equals(file.getUploadStatus())) {
            throw ContentErrors.stateChanged("文件已处理，请刷新");
        }
        long partSize = minioProperties.getMultipartPartSize().toBytes();
        int partCount = (int) ((file.getFileSize() + partSize - 1) / partSize);
        Map<Integer, String> submitted = new HashMap<>();
        for (MultipartCompleteRequest.PartRequest part : request.getParts()) {
            if (part.getPartNo() == null || part.getPartNo() < 1 || part.getPartNo() > partCount) {
                throw ContentErrors.param("分片不完整，请重新上传");
            }
            submitted.put(part.getPartNo(), stripQuotes(part.getEtag()));
        }
        for (int partNo = 1; partNo <= partCount; partNo++) {
            if (!submitted.containsKey(partNo)) {
                throw ContentErrors.param("分片不完整，请重新上传");
            }
        }
        List<UploadedPart> uploaded = objectStorage.listParts(file.getBucket(), file.getObjectKey(),
                file.getMultipartUploadId());
        Map<Integer, String> actual = new HashMap<>();
        for (UploadedPart part : uploaded) {
            actual.put(part.partNumber(), stripQuotes(part.etag()));
        }
        for (Map.Entry<Integer, String> entry : submitted.entrySet()) {
            if (!Objects.equals(actual.get(entry.getKey()), entry.getValue())) {
                throw ContentErrors.param("分片不完整，请重新上传");
            }
        }
        List<UploadedPart> parts = new ArrayList<>(uploaded);
        parts.sort(Comparator.comparingInt(UploadedPart::partNumber));
        objectStorage.completeMultipart(file.getBucket(), file.getObjectKey(), file.getMultipartUploadId(), parts);
        StoredObject stat = objectStorage.stat(file.getBucket(), file.getObjectKey());
        if (stat == null || (file.getFileSize() != null && stat.size() != file.getFileSize())) {
            markFailed(file, "合并后文件大小不一致，请重新上传");
            throw ContentErrors.param("分片不完整，请重新上传");
        }

        MediaInfo info = probe(file);
        if (info.durationMs() > MAX_VIDEO_MS + 500) {
            removeQuietly(file);
            markFailed(file, "只支持 MP4 和 MOV，不超过 2GB、30 分钟");
            throw new BizException(ErrorCode.VIDEO_TOO_LARGE, "只支持 MP4 和 MOV，不超过 2GB、30 分钟");
        }
        LocalDateTime now = BeijingTime.now();
        file.setUploadStatus(UploadStatus.READY.name());
        file.setTranscodeStatus(TranscodeStatus.WAITING.name());
        file.setTranscodeError(null);
        file.setDurationMs((int) Math.min(Integer.MAX_VALUE, info.durationMs()));
        file.setWidth(info.width() > 0 ? info.width() : null);
        file.setHeight(info.height() > 0 ? info.height() : null);
        file.setMultipartUploadId(file.getMultipartUploadId());
        fileMapper.updateById(file);
        // updateById 跳过 null：上次失败留下的转码错误要显式清空
        fileMapper.update(null, Wrappers.<FileEntity>lambdaUpdate()
                .eq(FileEntity::getId, file.getId())
                .set(FileEntity::getTranscodeError, null));

        resetVariants(file.getId(), false);
        writeLog("UPLOAD_COMPLETE", file.getId(), FileScene.valueOf(file.getScene()), file.getOriginalName());
        AfterCommit.run(() -> transcodeService.submit(file.getId()));
        return toVO(file);
    }

    @Override
    public AdminFileVO get(Long id) {
        FileEntity file = fileMapper.selectById(id);
        if (file == null) {
            throw ContentErrors.notFound();
        }
        requireView(FileScene.valueOf(file.getScene()));
        return toVO(file);
    }

    @Override
    @Transactional
    public AdminFileVO retryTranscode(Long id) {
        FileEntity file = fileMapper.selectById(id);
        if (file == null) {
            throw ContentErrors.notFound();
        }
        requireHandle(FileScene.valueOf(file.getScene()));
        if (!TranscodeStatus.FAILED.name().equals(file.getTranscodeStatus())) {
            throw ContentErrors.stateChanged("只有转码失败的视频可以重新转码");
        }
        if (!mediaSupport.rawExists(file)) {
            throw new BizException(ErrorCode.RECORD_DELETED, "原片已清理，请重新上传");
        }
        transcodeService.retry(id);
        writeLog("RETRY_TRANSCODE", file.getId(), FileScene.valueOf(file.getScene()), file.getOriginalName());
        return toVO(fileMapper.selectById(id));
    }

    @Override
    public FileDownloadVO download(Long id) {
        FileEntity file = fileMapper.selectById(id);
        if (file == null) {
            throw ContentErrors.notFound();
        }
        Long adminId = AdminContext.requireAdminId();
        boolean superAdmin = AdminContext.get() != null && AdminContext.get().isSuperAdmin();
        if (FileScene.EXPORT.name().equals(file.getScene())) {
            if (!superAdmin && !Objects.equals(file.getCreatedBy(), adminId)) {
                throw ContentErrors.notFound();
            }
            if (file.getExpireAt() != null && file.getExpireAt().isBefore(BeijingTime.now())) {
                throw new BizException(ErrorCode.RECORD_DELETED, "文件已过期，请重新导出");
            }
        } else {
            requireView(FileScene.valueOf(file.getScene()));
        }
        FileDownloadVO vo = new FileDownloadVO();
        vo.setFileId(file.getId());
        vo.setFileName(file.getOriginalName());
        vo.setUrl(objectStorage.presignedGetUrl(file.getBucket(), file.getObjectKey(), Duration.ofMinutes(10),
                file.getOriginalName()));
        vo.setExpireAt(BeijingTime.now().plusMinutes(10));
        return vo;
    }

    // ===== 组装与工具 =====

    private AdminFileVO toVO(FileEntity file) {
        AdminFileVO vo = new AdminFileVO();
        vo.setFileId(file.getId());
        vo.setScene(file.getScene());
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setMimeType(file.getMimeType());
        vo.setWidth(file.getWidth());
        vo.setHeight(file.getHeight());
        vo.setDurationSec(file.getDurationMs() == null ? null
                : (int) Math.round(file.getDurationMs() / 1000.0));
        vo.setUploadStatus(file.getUploadStatus());
        vo.setTranscodeStatus(file.getTranscodeStatus());
        vo.setStatus(ContentMediaSupport.status(file));
        vo.setTranscodeError(file.getTranscodeError());
        vo.setUrl(mediaSupport.previewUrl(file));
        vo.setCreatedAt(file.getCreatedAt());
        vo.setCoverFileId(file.getCoverFileId());
        if (file.getCoverFileId() != null) {
            FileEntity cover = fileMapper.selectById(file.getCoverFileId());
            vo.setCoverUrl(mediaSupport.publicUrl(cover));
        }
        for (FileVariant variant : mediaSupport.variants(file.getId())) {
            vo.getVariants().add(mediaSupport.variantVO(variant));
        }
        List<CoverCandidateVO> covers = mediaSupport.coverCandidates(file.getId());
        vo.setCoverCandidates(covers);
        if (vo.getCoverUrl() == null && !covers.isEmpty()) {
            vo.setCoverUrl(covers.get(0).getUrl());
        }
        if (TranscodeStatus.SUCCESS.name().equals(file.getTranscodeStatus())) {
            vo.setHlsUrl(objectStorage.hlsMasterUrl(file.getId()));
        }
        return vo;
    }

    private FileEntity requireOwnFile(Long id) {
        FileEntity file = id == null ? null : fileMapper.selectById(id);
        if (file == null) {
            throw ContentErrors.notFound();
        }
        if (!Objects.equals(file.getCreatedBy(), AdminContext.requireAdminId())
                && !(AdminContext.get() != null && AdminContext.get().isSuperAdmin())) {
            throw ContentErrors.notFound();
        }
        return file;
    }

    private MediaInfo probe(FileEntity file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("zmt-admin-video-", ".bin");
            try (InputStream in = objectStorage.get(file.getBucket(), file.getObjectKey())) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return mediaProcessor.probe(temp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取视频元数据失败 file_id={}", file.getId(), e);
            throw ContentErrors.param("视频读取失败，请重新上传");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响业务
                }
            }
        }
    }

    private void resetVariants(Long fileId, boolean failedOnly) {
        List<FileVariant> variants = mediaSupport.variants(fileId);
        if (variants.isEmpty()) {
            insertVariant(fileId, VariantCode.HLS_720P, 1280, 720);
            insertVariant(fileId, VariantCode.HLS_480P, 854, 480);
            return;
        }
        for (FileVariant variant : variants) {
            if (failedOnly && !TranscodeStatus.FAILED.name().equals(variant.getStatus())) {
                continue;
            }
            variant.setStatus(TranscodeStatus.WAITING.name());
            variant.setProgress(java.math.BigDecimal.ZERO);
            variant.setErrorMessage(null);
            variant.setStartedAt(null);
            variant.setFinishedAt(null);
            variantMapper.updateById(variant);
            // 同上：重试时清掉上次的错误信息和起止时间
            variantMapper.update(null, Wrappers.<FileVariant>lambdaUpdate()
                    .eq(FileVariant::getId, variant.getId())
                    .set(FileVariant::getErrorMessage, null)
                    .set(FileVariant::getStartedAt, null)
                    .set(FileVariant::getFinishedAt, null));
        }
    }

    private void insertVariant(Long fileId, VariantCode code, int width, int height) {
        FileVariant variant = new FileVariant();
        variant.setFileId(fileId);
        variant.setVariantCode(code.name());
        variant.setStatus(TranscodeStatus.WAITING.name());
        variant.setProgress(java.math.BigDecimal.ZERO);
        variant.setWidth(width);
        variant.setHeight(height);
        variant.setRetryCount(0);
        variantMapper.insert(variant);
    }

    private void markFailed(FileEntity file, String message) {
        file.setUploadStatus(UploadStatus.FAILED.name());
        file.setTranscodeStatus(TranscodeStatus.FAILED.name());
        file.setTranscodeError(message);
        fileMapper.updateById(file);
    }

    private void removeQuietly(FileEntity file) {
        try {
            objectStorage.remove(file.getBucket(), file.getObjectKey());
        } catch (RuntimeException e) {
            log.warn("删除合并后的视频对象失败 file_id={}", file.getId(), e);
        }
    }

    private void requireHandle(FileScene scene) {
        String permission = FileScenePermissions.handlePermission(scene);
        if (permission == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
        permissionService.require(permission);
    }

    private void requireView(FileScene scene) {
        String permission = FileScenePermissions.viewPermission(scene);
        if (permission == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
        permissionService.require(permission);
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String mime(String mime) {
        return mime == null || mime.isBlank() ? "video/mp4" : mime;
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        String value = etag.trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void writeLog(String action, Long fileId, FileScene scene, String name) {
        logs.record(OperationLogEntry.builder()
                .opType("COURSE")
                .opAction(action)
                .bizType("FILE")
                .bizId(fileId)
                .targetName(name)
                .extraText("场景 " + scene.name())
                .build());
    }
}
