package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.enums.VariantCode;
import com.zhimu.server.common.vo.FileVO;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 t_file 组装成 FileVO / MediaItemVO：临时地址（有效 2 小时）由 {@link ObjectStorage} 每次新签发，客户端以 file_id 作缓存键。
 */
@Component
@RequiredArgsConstructor
public class FileVoAssembler {

    private final FileMapper fileMapper;
    private final FileVariantMapper fileVariantMapper;
    private final ObjectStorage objectStorage;

    public FileVO toFileVo(FileEntity file) {
        if (file == null) {
            return null;
        }
        return FileVO.builder()
                .fileId(file.getId())
                .url(url(file))
                .thumbUrl(thumbUrl(file, null))
                .mediaType(file.getFileKind())
                .mimeType(file.getMimeType())
                .size(file.getFileSize())
                .width(file.getWidth())
                .height(file.getHeight())
                .durationSec(durationSec(file.getDurationMs()))
                .status(status(file))
                .build();
    }

    /** 批量组装的封面文件，避免逐条查询 */
    public FileVO toFileVo(FileEntity file, Map<Long, FileEntity> coverFiles) {
        if (file == null) {
            return null;
        }
        return FileVO.builder()
                .fileId(file.getId())
                .url(url(file))
                .thumbUrl(thumbUrl(file, coverFiles))
                .mediaType(file.getFileKind())
                .mimeType(file.getMimeType())
                .size(file.getFileSize())
                .width(file.getWidth())
                .height(file.getHeight())
                .durationSec(durationSec(file.getDurationMs()))
                .status(status(file))
                .build();
    }

    public MediaItemVO toMediaItem(FileEntity file, Integer sortNo, String sourceText, String shotBy,
                                   LocalDateTime shotAt, Map<Long, FileEntity> coverFiles) {
        FileVO vo = toFileVo(file, coverFiles);
        if (vo == null) {
            return null;
        }
        MediaItemVO item = new MediaItemVO();
        copy(vo, item);
        item.setSortNo(sortNo);
        item.setSourceText(sourceText);
        item.setShotBy(shotBy);
        item.setShotAt(shotAt);
        return item;
    }

    /** 图片缩略图（THUMB）或视频封面（cover_file_id）的临时地址 */
    public String thumbUrl(FileEntity file, Map<Long, FileEntity> coverFiles) {
        FileEntity cover = coverFile(file, coverFiles);
        if (cover != null) {
            return url(cover);
        }
        FileVariant thumb = fileVariantMapper.selectOneByCode(file.getId(), VariantCode.THUMB.name());
        if (thumb != null && thumb.getObjectKey() != null && !thumb.getObjectKey().isBlank()) {
            return objectStorage.presignedGetUrl(file.getBucket(), thumb.getObjectKey());
        }
        return null;
    }

    public FileEntity coverFile(FileEntity file, Map<Long, FileEntity> coverFiles) {
        if (file.getCoverFileId() == null) {
            return null;
        }
        if (coverFiles != null) {
            return coverFiles.get(file.getCoverFileId());
        }
        return fileMapper.selectById(file.getCoverFileId());
    }

    public String url(FileEntity file) {
        if (file == null || file.getObjectKey() == null) {
            return null;
        }
        return objectStorage.presignedGetUrl(file.getBucket(), file.getObjectKey());
    }

    /** FileVO.status：UPLOADING / FAILED；READY 且转码中为 PROCESSING；其余 READY */
    public String status(FileEntity file) {
        if (UploadStatus.UPLOADING.name().equals(file.getUploadStatus())) {
            return UploadStatus.UPLOADING.name();
        }
        if (UploadStatus.FAILED.name().equals(file.getUploadStatus())) {
            return UploadStatus.FAILED.name();
        }
        String transcode = file.getTranscodeStatus();
        if (TranscodeStatus.WAITING.name().equals(transcode) || TranscodeStatus.PROCESSING.name().equals(transcode)) {
            return "PROCESSING";
        }
        return UploadStatus.READY.name();
    }

    /** 批量取封面文件 */
    public Map<Long, FileEntity> loadCovers(Collection<FileEntity> files) {
        Map<Long, FileEntity> result = new LinkedHashMap<>();
        if (files == null || files.isEmpty()) {
            return result;
        }
        java.util.List<Long> coverIds = files.stream()
                .map(FileEntity::getCoverFileId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (coverIds.isEmpty()) {
            return result;
        }
        for (FileEntity cover : fileMapper.selectBatchIds(coverIds)) {
            result.put(cover.getId(), cover);
        }
        return result;
    }

    public static Integer durationSec(Integer durationMs) {
        if (durationMs == null) {
            return null;
        }
        return (int) Math.round(durationMs / 1000.0);
    }

    private static void copy(FileVO from, MediaItemVO to) {
        to.setFileId(from.getFileId());
        to.setUrl(from.getUrl());
        to.setThumbUrl(from.getThumbUrl());
        to.setMediaType(from.getMediaType());
        to.setMimeType(from.getMimeType());
        to.setSize(from.getSize());
        to.setWidth(from.getWidth());
        to.setHeight(from.getHeight());
        to.setDurationSec(from.getDurationSec());
        to.setStatus(from.getStatus());
    }
}
