package com.zhimu.server.admin.content.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.vo.AdminFileVO;
import com.zhimu.server.admin.content.vo.CoverCandidateVO;
import com.zhimu.server.admin.content.vo.VideoInfoVO;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.support.CourseCoverResolver;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容运营的文件读取与绑定：课程 / 专题 / 知识条目只通过 t_file 的 file_id 关联，
 * 展示地址每次由对象存储重新签发；HLS 与封面使用公开前缀的固定地址。
 */
@Component
@RequiredArgsConstructor
public class ContentMediaSupport {

    private final FileMapper fileMapper;
    private final FileVariantMapper fileVariantMapper;
    private final ObjectStorage objectStorage;
    private final CourseCoverResolver coverResolver;

    public String coverUrl(Course course) {
        return coverResolver.resolve(course);
    }

    public String coverUrl(CourseTopic topic) {
        return coverResolver.resolve(topic);
    }

    public FileEntity require(Long id) {
        if (id == null) {
            return null;
        }
        FileEntity file = fileMapper.selectById(id);
        if (file == null) {
            throw ContentErrors.field("file", "文件不存在，请重新上传");
        }
        return file;
    }

    public FileEntity requireAdminFile(Long id, FileScene scene, boolean mustReady) {
        FileEntity file = require(id);
        if (file == null) {
            return null;
        }
        if (!scene.name().equals(file.getScene()) || !"ADMIN".equals(file.getUploaderType())) {
            throw ContentErrors.field("file", "文件无效，请重新上传");
        }
        if (mustReady && !UploadStatus.READY.name().equals(file.getUploadStatus())) {
            throw ContentErrors.field("file", "文件尚未上传完成，请稍后重试");
        }
        if (UploadStatus.FAILED.name().equals(file.getUploadStatus())) {
            throw ContentErrors.field("file", "上传失败，请重新上传");
        }
        return file;
    }

    public FileEntity requireCourseVideo(Long id) {
        return requireAdminFile(id, FileScene.COURSE_VIDEO, false);
    }

    public FileEntity requireCover(Long id, FileScene scene) {
        return requireAdminFile(id, scene, true);
    }

    public String previewUrl(FileEntity file) {
        if (file == null || file.getObjectKey() == null) {
            return null;
        }
        return objectStorage.presignedGetUrl(file.getBucket(), file.getObjectKey());
    }

    public String publicUrl(FileEntity file) {
        if (file == null || file.getObjectKey() == null) {
            return null;
        }
        return objectStorage.publicUrl(file.getBucket(), file.getObjectKey());
    }

    public String publicUrl(String bucket, String objectKey) {
        return objectStorage.publicUrl(bucket, objectKey);
    }

    public void markBound(Long fileId, boolean bound) {
        if (fileId != null) {
            fileMapper.updateBound(fileId, bound ? 1 : 0, BeijingTime.now());
        }
    }

    public List<FileVariant> variants(Long fileId) {
        if (fileId == null) {
            return List.of();
        }
        return fileVariantMapper.selectList(Wrappers.<FileVariant>lambdaQuery()
                .eq(FileVariant::getFileId, fileId)
                .orderByAsc(FileVariant::getId));
    }

    public Map<String, FileVariant> variantMap(Long fileId) {
        Map<String, FileVariant> result = new LinkedHashMap<>();
        for (FileVariant variant : variants(fileId)) {
            result.put(variant.getVariantCode(), variant);
        }
        return result;
    }

    public List<CoverCandidateVO> coverCandidates(Long videoFileId) {
        List<CoverCandidateVO> result = new ArrayList<>();
        if (videoFileId == null) {
            return result;
        }
        List<FileEntity> covers = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                .eq(FileEntity::getParentFileId, videoFileId)
                .eq(FileEntity::getScene, FileScene.VIDEO_COVER.name())
                .orderByAsc(FileEntity::getId));
        for (FileEntity cover : covers) {
            CoverCandidateVO vo = new CoverCandidateVO();
            vo.setFileId(cover.getId());
            vo.setUrl(publicUrl(cover));
            result.add(vo);
        }
        return result;
    }

    public VideoInfoVO videoInfo(FileEntity file) {
        if (file == null) {
            return null;
        }
        VideoInfoVO vo = new VideoInfoVO();
        vo.setFileId(file.getId());
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setDurationSec(file.getDurationMs() == null ? null
                : (int) Math.round(file.getDurationMs() / 1000.0));
        vo.setWidth(file.getWidth());
        vo.setHeight(file.getHeight());
        vo.setUploadedAt(file.getCreatedAt());
        vo.setUploadStatus(file.getUploadStatus());
        vo.setTranscodeStatus(file.getTranscodeStatus());
        vo.setTranscodeError(file.getTranscodeError());
        vo.setCoverFileId(file.getCoverFileId());
        return vo;
    }

    public AdminFileVO.VariantVO variantVO(FileVariant variant) {
        AdminFileVO.VariantVO vo = new AdminFileVO.VariantVO();
        vo.setVariantCode(variant.getVariantCode());
        vo.setStatus(variant.getStatus());
        vo.setProgress(variant.getProgress());
        vo.setWidth(variant.getWidth());
        vo.setHeight(variant.getHeight());
        vo.setFileSize(variant.getFileSize());
        vo.setErrorMessage(variant.getErrorMessage());
        return vo;
    }

    public static String status(FileEntity file) {
        if (file == null) {
            return null;
        }
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

    /** 视频原片对象是否还在（转码成功后 7 天清理） */
    public boolean rawExists(FileEntity file) {
        return file != null && file.getObjectKey() != null
                && objectStorage.exists(file.getBucket(), file.getObjectKey());
    }
}
