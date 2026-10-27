package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseCoverResolverTest {
    private final FileMapper files = mock(FileMapper.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final CourseCoverResolver resolver = new CourseCoverResolver(files, storage);

    private FileEntity image(String key) {
        FileEntity file = new FileEntity();
        file.setBucket("zmt-course"); file.setObjectKey(key); file.setUploadStatus("READY");
        return file;
    }

    @Test void boundCoverUsesTheCurrentStorageEndpointInsteadOfAnOldLocalUrl() {
        Course course = new Course(); course.setCoverFileId(11L);
        course.setCoverUrl("http://127.0.0.1:9000/zmt-course/cover.png");
        when(files.selectById(11L)).thenReturn(image("cover.png"));
        when(storage.publicUrl("zmt-course", "cover.png")).thenReturn("https://media.current/cover.png");
        assertEquals("https://media.current/cover.png", resolver.resolve(course));
    }

    @Test void missingCoverUsesTheSameVideosGeneratedFrame() {
        Course course = new Course(); course.setVideoFileId(20L);
        course.setCoverUrl("https://cdn.zhimutong.example.com/course/1.jpg");
        FileEntity video = new FileEntity(); video.setCoverFileId(21L);
        when(files.selectById(20L)).thenReturn(video);
        when(files.selectById(21L)).thenReturn(image("frame.jpg"));
        when(storage.publicUrl("zmt-course", "frame.jpg")).thenReturn("https://media.current/frame.jpg");
        assertEquals("https://media.current/frame.jpg", resolver.resolve(course));
    }

    @Test void legacyCourseWithoutRealMaterialDoesNotInventAnImage() {
        Course course = new Course(); course.setCoverUrl("https://cdn.zhimutong.example.com/course/1.jpg");
        assertNull(resolver.resolve(course));
        verifyNoInteractions(files, storage);
    }

    @Test void legitimateLegacyCoverIsPreserved() {
        Course course = new Course(); course.setCoverUrl("https://media.current/legacy.jpg");
        assertEquals(course.getCoverUrl(), resolver.resolve(course));
        verifyNoInteractions(files, storage);
    }
}
