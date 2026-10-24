package com.zhimu.server.admin.content.service.impl;

import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.vo.AdminFileVO;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.media.ImageProcessor;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.storage.MinioProperties;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-S37/S38：场景权限与转码失败重试状态。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminFileServiceImplTest {

    @Mock private FileMapper fileMapper;
    @Mock private FileVariantMapper variantMapper;
    @Mock private ObjectStorage objectStorage;
    @Mock private MediaProcessor mediaProcessor;
    @Mock private ImageProcessor imageProcessor;
    @Mock private MinioProperties minioProperties;
    @Mock private TranscodeService transcodeService;
    @Mock private AdminPermissionService permissionService;
    @Mock private ContentMediaSupport mediaSupport;
    @Mock private ContentLogs logs;

    @InjectMocks private AdminFileServiceImpl service;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.content.ContentTableInfo.ensure();
        AdminContext.set(new AdminContext.LoginAdmin(1L, "郑琳", Set.of("OPERATOR"), Set.of(), 0, "t"));
        lenient().when(mediaSupport.variants(7L)).thenReturn(List.of());
        lenient().when(mediaSupport.coverCandidates(7L)).thenReturn(List.of());
        lenient().when(objectStorage.hlsMasterUrl(7L)).thenReturn("http://127.0.0.1:9000/zmt-course/hls/7/index.m3u8");
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void retryUsesHandlePermissionAndRestartsFailedTranscode() {
        FileEntity file = file(TranscodeStatus.FAILED.name(), UploadStatus.READY.name());
        when(fileMapper.selectById(7L)).thenReturn(file);
        when(mediaSupport.rawExists(file)).thenReturn(true);

        AdminFileVO vo = service.retryTranscode(7L);

        verify(permissionService).require("WA-C03:HANDLE");
        verify(transcodeService).retry(7L);
        assertThat(vo.getFileId()).isEqualTo(7L);
        assertThat(vo.getTranscodeStatus()).isEqualTo("FAILED");
    }

    @Test
    void retryRejectsNonFailedFileWith40903() {
        FileEntity file = file(TranscodeStatus.SUCCESS.name(), UploadStatus.READY.name());
        when(fileMapper.selectById(7L)).thenReturn(file);

        assertThatThrownBy(() -> service.retryTranscode(7L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903));
        verify(transcodeService, never()).retry(7L);
    }

    @Test
    void getRequiresSceneViewPermissionAndReturnsTranscodeState() {
        FileEntity file = file(TranscodeStatus.PROCESSING.name(), UploadStatus.READY.name());
        when(fileMapper.selectById(7L)).thenReturn(file);

        AdminFileVO vo = service.get(7L);

        verify(permissionService).require("WA-C03:VIEW");
        assertThat(vo.getStatus()).isEqualTo("PROCESSING");
        assertThat(vo.getScene()).isEqualTo("COURSE_VIDEO");
    }

    private static FileEntity file(String transcode, String upload) {
        FileEntity file = new FileEntity();
        file.setId(7L);
        file.setBucket("zmt-course");
        file.setObjectKey("raw/20261023/demo.mp4");
        file.setOriginalName("冬季鸡舍通风与保温.mp4");
        file.setScene("COURSE_VIDEO");
        file.setFileKind("VIDEO");
        file.setMimeType("video/mp4");
        file.setFileSize(1000L);
        file.setUploadStatus(upload);
        file.setTranscodeStatus(transcode);
        file.setUploaderType("ADMIN");
        file.setCreatedBy(1L);
        return file;
    }
}
