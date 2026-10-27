package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/** 封面按绑定的对象重新生成当前环境地址；缺失时才回退到同一视频的真实抽帧。 */
@Component
@RequiredArgsConstructor
public class CourseCoverResolver {
    private final FileMapper fileMapper;
    private final ObjectStorage storage;

    public String resolve(Course course) {
        String cover = fileUrl(course.getCoverFileId());
        if (cover != null) return cover;
        String legacy = usableLegacyUrl(course.getCoverUrl());
        if (legacy != null) return legacy;
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        return video == null ? null : fileUrl(video.getCoverFileId());
    }

    public String resolve(CourseTopic topic) {
        String cover = fileUrl(topic.getCoverFileId());
        return cover != null ? cover : usableLegacyUrl(topic.getCoverUrl());
    }

    private String fileUrl(Long id) {
        if (id == null || id <= 0) return null;
        FileEntity file = fileMapper.selectById(id);
        if (file == null || !"READY".equals(file.getUploadStatus())
                || file.getBucket() == null || file.getBucket().isBlank()
                || file.getObjectKey() == null || file.getObjectKey().isBlank()) return null;
        return storage.publicUrl(file.getBucket(), file.getObjectKey());
    }

    private static String usableLegacyUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return null;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("example.com") || host.endsWith(".example.com") ? null : url.trim();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
