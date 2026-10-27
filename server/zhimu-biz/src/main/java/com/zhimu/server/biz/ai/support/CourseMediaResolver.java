package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import com.zhimu.server.biz.ai.mapper.CourseMediaMapper;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Published courses play their bound video; unbound legacy courses keep their existing media rows. */
@Component
@RequiredArgsConstructor
public class CourseMediaResolver {
    private final CourseMediaMapper mediaMapper;
    private final FileVariantMapper variantMapper;
    private final ObjectStorage storage;

    public List<CourseMedia> resolve(Course course) {
        Long fileId = course.getVideoFileId();
        if (fileId == null || fileId <= 0) return mediaMapper.selectPlayable(course.getId());
        List<CourseMedia> result = new ArrayList<>();
        for (String quality : List.of(AiCodes.QUALITY_720, AiCodes.QUALITY_480)) {
            String directory = AiCodes.QUALITY_720.equals(quality) ? "720p" : "480p";
            FileVariant variant = variantMapper.selectOneByCode(fileId, "HLS_" + directory.toUpperCase(java.util.Locale.ROOT));
            if (variant == null || !"SUCCESS".equals(variant.getStatus())
                    || variant.getObjectKey() == null || variant.getObjectKey().isBlank()) continue;
            CourseMedia source = new CourseMedia();
            source.setCourseId(course.getId());
            source.setMediaType("HLS");
            source.setQuality(quality);
            source.setUrl(storage.hlsUrl(fileId, directory));
            source.setSizeBytes(variant.getFileSize() == null ? 0L : variant.getFileSize());
            source.setStatus("SUCCESS");
            result.add(source);
        }
        return result;
    }
}
