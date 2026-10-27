package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import com.zhimu.server.biz.ai.mapper.CourseMediaMapper;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseMediaResolverTest {
    private final CourseMediaMapper legacy = mock(CourseMediaMapper.class);
    private final FileVariantMapper variants = mock(FileVariantMapper.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final CourseMediaResolver resolver = new CourseMediaResolver(legacy, variants, storage);

    private Course course(Long fileId) {
        Course course = new Course(); course.setId(6305L); course.setVideoFileId(fileId); return course;
    }
    private FileVariant variant(String status, Long size) {
        FileVariant result = new FileVariant(); result.setStatus(status);
        result.setObjectKey("derived/hls/240108/720p/index.m3u8"); result.setFileSize(size); return result;
    }

    @Test void boundVideoProducesBothRealHlsSourcesWithoutLegacyRows() {
        when(variants.selectOneByCode(240108L, "HLS_720P")).thenReturn(variant("SUCCESS", 1000L));
        when(variants.selectOneByCode(240108L, "HLS_480P")).thenReturn(variant("SUCCESS", 700L));
        when(storage.hlsUrl(240108L, "720p")).thenReturn("http://media/240108/720p/index.m3u8");
        when(storage.hlsUrl(240108L, "480p")).thenReturn("http://media/240108/480p/index.m3u8");
        List<CourseMedia> media = resolver.resolve(course(240108L));
        assertEquals(List.of("P720", "P480"), media.stream().map(CourseMedia::getQuality).toList());
        assertEquals("http://media/240108/720p/index.m3u8", media.get(0).getUrl());
        assertEquals(700L, media.get(1).getSizeBytes());
        verifyNoInteractions(legacy);
    }

    @Test void unboundLegacyCourseKeepsItsExistingPlayableRows() {
        List<CourseMedia> rows = List.of(new CourseMedia());
        when(legacy.selectPlayable(6305L)).thenReturn(rows);
        assertSame(rows, resolver.resolve(course(null)));
        verifyNoInteractions(variants, storage);
    }

    @Test void replacingVideoNeverFallsBackToTheOldCourseMedia() {
        when(variants.selectOneByCode(999L, "HLS_720P")).thenReturn(variant("WAITING", 1L));
        when(variants.selectOneByCode(999L, "HLS_480P")).thenReturn(variant("FAILED", 1L));
        assertTrue(resolver.resolve(course(999L)).isEmpty());
        verifyNoInteractions(legacy, storage);
    }

    @Test void onlySuccessfulAndPresentVariantsArePlayable() {
        when(variants.selectOneByCode(240108L, "HLS_720P")).thenReturn(variant("SUCCESS", null));
        when(storage.hlsUrl(240108L, "720p")).thenReturn("http://media/240108/720p/index.m3u8");
        List<CourseMedia> media = resolver.resolve(course(240108L));
        assertEquals(1, media.size());
        assertEquals(0L, media.get(0).getSizeBytes());
        verifyNoInteractions(legacy);
    }

    @Test void successMetadataWithoutAnOutputDoesNotCreateABrokenSource() {
        FileVariant broken = variant("SUCCESS", 100L); broken.setObjectKey("");
        when(variants.selectOneByCode(240108L, "HLS_720P")).thenReturn(broken);
        assertTrue(resolver.resolve(course(240108L)).isEmpty());
        verifyNoInteractions(storage, legacy);
    }
}
