package com.zhimu.server.admin.content.service.impl;

import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.mapper.*;
import com.zhimu.server.admin.content.request.CourseSaveRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.support.*;
import com.zhimu.server.biz.ai.entity.*;
import com.zhimu.server.biz.ai.mapper.*;
import com.zhimu.server.biz.common.entity.*;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.context.AdminContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseAdminShelfTest {
    @Mock CourseMapper courseMapper;
    @Mock CourseTopicMapper topicMapper;
    @Mock CourseCategoryMapper categoryMapper;
    @Mock CourseMediaMapper mediaMapper;
    @Mock AiKnowledgeMapper knowledgeMapper;
    @Mock FileMapper fileMapper;
    @Mock ContentApplicableMapper applicableMapper;
    @Mock ContentStatMapper statMapper;
    @Mock ContentCategoryAdminService categoryService;
    @Mock ContentMediaSupport media;
    @Mock ContentNames names;
    @Mock ContentLogs logs;
    @Mock TranscodeService transcodeService;
    @InjectMocks CourseAdminServiceImpl service;
    Course course;
    CourseCategory category;
    FileEntity cover;
    List<CourseMedia> legacy;

    @BeforeEach void setUp() {
        ContentTableInfo.ensure();
        AdminContext.set(new AdminContext.LoginAdmin(2L, "审核员", Set.of("SUPER_ADMIN"), Set.of(), 0, "test"));
        course = new Course(); course.setId(6302L); course.setTitle("历史课程");
        course.setCategoryId(1L); course.setStatus("OFF_SHELF"); course.setTranscodeStatus("SUCCESS");
        course.setCoverFileId(80L); course.setCoverSource("AUTO"); course.setVersion(3);
        category = new CourseCategory(); category.setId(1L); category.setName("教学"); category.setStatus("ENABLED");
        cover = new FileEntity(); cover.setId(80L); cover.setUploadStatus("READY");
        cover.setBucket("course"); cover.setObjectKey("cover.png");
        legacy = new ArrayList<>(List.of(legacy("P720"), legacy("P480")));
        when(courseMapper.selectById(6302L)).thenReturn(course);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        when(categoryMapper.selectById(1L)).thenReturn(category);
        when(categoryService.requireEnabled(1L)).thenReturn(category);
        when(fileMapper.selectById(80L)).thenReturn(cover);
        when(media.variantMap(any())).thenReturn(Map.of());
        when(media.coverUrl(any(Course.class))).thenReturn("https://media.test/cover.png");
        when(media.publicUrl(cover)).thenReturn("https://media.test/cover.png");
        when(mediaMapper.selectPlayable(6302L)).thenAnswer(c -> legacy);
        when(mediaMapper.selectPlayableByCourseIds(anyList())).thenAnswer(c -> legacy);
    }
    @AfterEach void clear() { AdminContext.clear(); }

    @Test void legacyCourseHasSameReadyStateInDetailPollingAndReshelving() {
        var detail = service.detail(6302L);
        assertThat(detail.getCanOnShelf()).isTrue();
        assertThat(detail.getP720().getStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getP480().getStatus()).isEqualTo("SUCCESS");
        assertThat(service.transcodeStates("6302").getFirst().getTranscodeStatus()).isEqualTo("SUCCESS");
        assertThat(service.onShelf(6302L, 3).getStatus()).isEqualTo("ON_SHELF");
        verify(courseMapper).updateById(course);
        verify(logs).record(any());
    }
    @Test void missingEitherLegacyQualityCannotPublish() {
        for (String quality : List.of("P720", "P480")) {
            legacy = new ArrayList<>(List.of(legacy(quality)));
            assertBlocked();
        }
    }
    @Test void blankOrPlaceholderLegacyUrlCannotClaimSuccess() {
        legacy.getFirst().setUrl(" "); assertBlocked();
        legacy.getFirst().setUrl("https://cdn.example.com/fake.m3u8"); assertBlocked();
    }
    @Test void failedLegacyMediaCannotPublish() {
        legacy.getFirst().setStatus("FAILED"); assertBlocked();
    }
    @Test void replacementVideoMustNeverFallBackToOldPlayableMedia() {
        FileEntity video = video("PROCESSING");
        when(media.variantMap(90L)).thenReturn(Map.of("HLS_720P", variant("SUCCESS"), "HLS_480P", variant("SUCCESS")));
        assertBlocked();
        when(fileMapper.selectById(90L)).thenReturn(null);
        assertBlocked();
        assertThat(video.getTranscodeStatus()).isEqualTo("PROCESSING");
    }
    @Test void boundVideoRequiresBothRealRenditions() {
        video("SUCCESS");
        when(media.variantMap(90L)).thenReturn(Map.of("HLS_720P", variant("SUCCESS")));
        assertBlocked();
        FileVariant bad = variant("SUCCESS"); bad.setObjectKey("");
        when(media.variantMap(90L)).thenReturn(Map.of("HLS_720P", variant("SUCCESS"), "HLS_480P", bad));
        assertBlocked();
        when(media.variantMap(90L)).thenReturn(Map.of("HLS_720P", variant("SUCCESS"), "HLS_480P", variant("SUCCESS")));
        assertThat(service.detail(6302L).getCanOnShelf()).isTrue();
        assertThat(service.onShelf(6302L, 3).getStatus()).isEqualTo("ON_SHELF");
    }
    @Test void editingLegacyMetadataDoesNotRequireReuploadingItsVideoOrCover() {
        CourseSaveRequest request = new CourseSaveRequest(); request.setTitle("修正后的标题");
        request.setCategoryId(1L); request.setCoverFileId(80L); request.setCoverSource("AUTO");
        request.setVersion(3); request.setSubmitAction("ON_SHELF");
        assertThat(service.update(6302L, request).getStatus()).isEqualTo("ON_SHELF");
        assertThat(course.getVideoFileId()).isNull();
        assertThat(course.getTitle()).isEqualTo("修正后的标题");
        verify(media, never()).requireCourseVideo(any());
    }
    @Test void missingCoverAndDisabledCategoryRemainBlocked() {
        cover.setUploadStatus("UPLOADING");
        assertThat(service.detail(6302L).getCanOnShelf()).isFalse();
        assertThatThrownBy(() -> service.onShelf(6302L, 3)).hasMessageContaining("封面");
        cover.setUploadStatus("READY"); category.setStatus("DISABLED");
        assertThat(service.detail(6302L).getCanOnShelf()).isFalse();
        assertThatThrownBy(() -> service.onShelf(6302L, 3)).hasMessageContaining("分类");
        verify(courseMapper, never()).updateById(any(Course.class));
    }
    @Test void staleVersionNeverWrites() {
        assertThatThrownBy(() -> service.onShelf(6302L, 2)).hasMessageContaining("内容已更新");
        verify(courseMapper, never()).updateById(any(Course.class));
    }
    private void assertBlocked() {
        assertThat(service.detail(6302L).getCanOnShelf()).isFalse();
        assertThat(service.detail(6302L).getTranscodeStatus()).isNotEqualTo("SUCCESS");
        assertThatThrownBy(() -> service.onShelf(6302L, 3)).hasMessageContaining("转码完成");
        assertThat(course.getStatus()).isEqualTo("OFF_SHELF");
        verify(courseMapper, never()).updateById(any(Course.class));
    }
    private FileEntity video(String status) {
        course.setVideoFileId(90L); FileEntity video = new FileEntity(); video.setId(90L);
        video.setUploadStatus("READY"); video.setTranscodeStatus(status);
        when(fileMapper.selectById(90L)).thenReturn(video); return video;
    }
    private static CourseMedia legacy(String quality) {
        CourseMedia row = new CourseMedia(); row.setCourseId(6302L); row.setQuality(quality);
        row.setMediaType("HLS"); row.setStatus("SUCCESS"); row.setUrl("https://media.test/" + quality + ".m3u8"); return row;
    }
    private static FileVariant variant(String status) {
        FileVariant v = new FileVariant(); v.setStatus(status); v.setObjectKey("hls/index.m3u8"); return v;
    }
}
