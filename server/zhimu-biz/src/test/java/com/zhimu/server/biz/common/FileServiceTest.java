package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.controller.FileController;
import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.impl.FileServiceImpl;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.FileVO;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.integration.media.ImageProcessor;
import com.zhimu.server.integration.media.MediaInfo;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-U05–U07：图片 / 视频上传、缩略图与封面、文件信息与删除。
 */
class FileServiceTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final FileMapper fileMapper = mock(FileMapper.class);
    private final FileVariantMapper variantMapper = mock(FileVariantMapper.class);
    private final BizMediaMapper bizMediaMapper = mock(BizMediaMapper.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MediaProcessor mediaProcessor = mock(MediaProcessor.class);
    private final FarmMemberCache farmMemberCache = mock(FarmMemberCache.class);

    private final Map<Long, FileEntity> files = new LinkedHashMap<>();
    private final Map<String, FileVariant> variants = new LinkedHashMap<>();
    private final AtomicLong fileId = new AtomicLong(500);
    private final AtomicLong variantId = new AtomicLong(1);
    private final AtomicLong objectSeq = new AtomicLong(1);

    private FileServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        FileVoAssembler assembler = new FileVoAssembler(fileMapper, variantMapper, storage);
        service = new FileServiceImpl(fileMapper, variantMapper, bizMediaMapper, storage, mediaProcessor,
                new ImageProcessor(), assembler, farmMemberCache);
        mvc = TestMvc.builder(new FileController(service)).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));

        when(fileMapper.insert(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(fileId.incrementAndGet());
            files.put(entity.getId(), entity);
            return 1;
        });
        when(fileMapper.updateById(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            files.put(entity.getId(), entity);
            return 1;
        });
        when(fileMapper.selectById(any())).thenAnswer(invocation -> files.get((Long) invocation.getArgument(0)));
        when(fileMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            List<FileEntity> result = new ArrayList<>();
            for (Object id : (Iterable<?>) invocation.getArgument(0)) {
                FileEntity entity = files.get((Long) id);
                if (entity != null) {
                    result.add(entity);
                }
            }
            return result;
        });
        when(fileMapper.deleteById(anyLong())).thenAnswer(invocation -> {
            files.remove((Long) invocation.getArgument(0));
            return 1;
        });
        when(variantMapper.insert(any(FileVariant.class))).thenAnswer(invocation -> {
            FileVariant variant = invocation.getArgument(0);
            variant.setId(variantId.incrementAndGet());
            variants.put(variant.getFileId() + ":" + variant.getVariantCode(), variant);
            return 1;
        });
        when(variantMapper.selectOneByCode(any(), anyString())).thenAnswer(invocation ->
                variants.get(invocation.getArgument(0) + ":" + invocation.getArgument(1)));
        when(variantMapper.deleteByFile(any())).thenAnswer(invocation -> {
            variants.keySet().removeIf(key -> key.startsWith(invocation.getArgument(0) + ":"));
            return 1;
        });
        when(bizMediaMapper.countActiveByFile(any())).thenReturn(0L);

        when(storage.bucketOf(any())).thenReturn("zmt-private");
        when(storage.buildObjectKey(any(FileScene.class), anyString())).thenAnswer(invocation ->
                ((FileScene) invocation.getArgument(0)).name().toLowerCase() + "/20261019/object-"
                        + objectSeq.getAndIncrement() + "." + invocation.getArgument(1));
        when(storage.presignedGetUrl(anyString(), anyString())).thenAnswer(invocation ->
                "https://files.test/" + invocation.getArgument(1));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void uploadImageCreatesThumbnailAndReturnsFileVo() {
        byte[] png = png(800, 600);
        FileVO vo = service.upload(new MockMultipartFile("file", "pig.png", "image/png", png),
                FileScene.INSPECTION, null, null, LocalDateTime.of(2026, 10, 19, 9, 10), 1001L);

        assertThat(vo.getFileId()).isNotNull();
        assertThat(vo.getMediaType()).isEqualTo("IMAGE");
        assertThat(vo.getMimeType()).isEqualTo("image/png");
        assertThat(vo.getWidth()).isEqualTo(800);
        assertThat(vo.getHeight()).isEqualTo(600);
        assertThat(vo.getStatus()).isEqualTo("READY");
        assertThat(vo.getSize()).isEqualTo((long) png.length);
        assertThat(vo.getUrl()).startsWith("https://files.test/inspection/");
        assertThat(vo.getThumbUrl()).startsWith("https://files.test/inspection/");

        FileEntity stored = files.get(vo.getFileId());
        assertThat(stored.getFileKind()).isEqualTo("IMAGE");
        assertThat(stored.getScene()).isEqualTo("INSPECTION");
        assertThat(stored.getFarmId()).isEqualTo(1001L);
        assertThat(stored.getUploadStatus()).isEqualTo("READY");
        assertThat(stored.getShotAt()).as("图片忽略 shot_at").isNull();
        assertThat(stored.getBound()).isZero();
        assertThat(variants).containsKey(vo.getFileId() + ":THUMB");
    }

    @Test
    void farmQualAndAiImageStoreEmptyFarmId() {
        byte[] png = png(100, 100);
        FileVO qual = service.upload(new MockMultipartFile("file", "qual.png", "image/png", png),
                FileScene.FARM_QUAL, null, null, null, 1001L);
        FileVO ai = service.upload(new MockMultipartFile("file", "ai.png", "image/png", png),
                FileScene.AI_IMAGE, null, null, null, 1001L);

        assertThat(files.get(qual.getFileId()).getFarmId()).isNull();
        assertThat(files.get(ai.getFileId()).getFarmId()).isNull();
    }

    @Test
    void uploadRejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> service.upload(file, FileScene.INSPECTION, null, null, null, 1001L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FILE_INVALID.getCode()));
    }

    @Test
    void uploadRejectsImageLargerThanFiveMegabytes() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(6L * 1024 * 1024);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(PNG_SIGNATURE));

        assertThatThrownBy(() -> service.upload(file, FileScene.INSPECTION, null, null, null, 1001L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FILE_INVALID.getCode()));
    }

    @Test
    void uploadVideoWithAutoCoverCreatesCoverFile() throws IOException {
        when(mediaProcessor.probe(any(Path.class))).thenReturn(new MediaInfo(18_000, 1280, 720, "h264", "aac"));
        byte[] cover = jpeg(320, 180);
        doAnswer(invocation -> {
            Path out = invocation.getArgument(2);
            Files.write(out, cover);
            return null;
        }).when(mediaProcessor).extractFrame(any(Path.class), anyDouble(), any(Path.class));

        FileVO vo = service.upload(new MockMultipartFile("file", "inspect.mp4", "video/mp4", mp4()),
                FileScene.INSPECTION, null, 18_000L, LocalDateTime.of(2026, 10, 19, 9, 10, 30), 1001L);

        assertThat(vo.getMediaType()).isEqualTo("VIDEO");
        assertThat(vo.getDurationSec()).isEqualTo(18);
        assertThat(vo.getStatus()).isEqualTo("READY");
        assertThat(vo.getWidth()).isEqualTo(1280);
        assertThat(vo.getHeight()).isEqualTo(720);
        assertThat(vo.getThumbUrl()).isNotNull();

        FileEntity video = files.get(vo.getFileId());
        assertThat(video.getFileKind()).isEqualTo("VIDEO");
        assertThat(video.getDurationMs()).isEqualTo(18_000);
        assertThat(video.getCoverFileId()).isNotNull();
        assertThat(video.getShotAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 10, 30));
        FileEntity coverFile = files.get(video.getCoverFileId());
        assertThat(coverFile.getScene()).isEqualTo("VIDEO_COVER");
        assertThat(coverFile.getParentFileId()).isEqualTo(video.getId());
    }

    @Test
    void uploadVideoRejectsOverThirtySeconds() {
        when(mediaProcessor.probe(any(Path.class))).thenReturn(new MediaInfo(31_000, 1280, 720, "h264", "aac"));
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.mp4", "video/mp4", mp4()),
                FileScene.INSPECTION, null, null, null, 1001L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException ex = (BizException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VIDEO_TOO_LARGE.getCode());
                    assertThat(ex.getMessage()).isEqualTo("视频不能超过 30 秒");
                });
    }

    @Test
    void uploadVideoRejectsOverThirtyMegabytes() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(31L * 1024 * 1024);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(mp4()));

        assertThatThrownBy(() -> service.upload(file, FileScene.INSPECTION, null, null, null, 1001L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException ex = (BizException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VIDEO_TOO_LARGE.getCode());
                    assertThat(ex.getMessage()).isEqualTo("视频不能超过 30MB");
                });
    }

    @Test
    void getReturnsFreshUrlForOwner() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 0);

        mvc.perform(get("/api/v1/files/" + file.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_id").value(file.getId()))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("https://files.test/")))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void getRejectsOtherUserWhenFileNotBound() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 0);
        CommonFlowFixture.login(10002L);

        mvc.perform(get("/api/v1/files/" + file.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void getAllowsFarmMemberWhenFileIsBound() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 1);
        when(bizMediaMapper.selectActiveByFile(file.getId())).thenReturn(List.of(
                media(file.getId(), 1001L)));
        when(farmMemberCache.get(1001L, 10002L)).thenReturn(member(1001L, 10002L, FarmRole.MANAGER));
        CommonFlowFixture.login(10002L);

        mvc.perform(get("/api/v1/files/" + file.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_id").value(file.getId()));
    }

    @Test
    void deleteRejectsBoundFileWith40903() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 1);
        when(bizMediaMapper.countActiveByFile(file.getId())).thenReturn(1L);

        mvc.perform(delete("/api/v1/files/" + file.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value("这个文件已用在记录中，请在记录里删除"));
        verify(storage, never()).remove(anyString(), anyString());
    }

    @Test
    void deleteRemovesObjectAndRecordForUnboundFile() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 0);
        when(bizMediaMapper.countActiveByFile(file.getId())).thenReturn(0L);

        mvc.perform(delete("/api/v1/files/" + file.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(storage).remove("zmt-private", file.getObjectKey());
        verify(fileMapper).deleteById(file.getId());
    }

    @Test
    void deleteRejectsOtherUser() throws Exception {
        FileEntity file = storeFile(10001L, 1001L, 0);
        CommonFlowFixture.login(10002L);
        mvc.perform(delete("/api/v1/files/" + file.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        verify(fileMapper, never()).deleteById(file.getId());
    }

    @Test
    void u05ControllerAcceptsMultipartWithoutFarmHeader() throws Exception {
        byte[] png = png(120, 80);
        mvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .param("scene", "INSPECTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_id").isNumber())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.media_type").value("IMAGE"));
        assertThat(files.values()).anySatisfy(entity -> assertThat(entity.getFarmId()).isNull());
    }

    @Test
    void u05ControllerRejectsUnknownScene() throws Exception {
        mvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png(10, 10)))
                        .param("scene", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("scene"));
    }

    @Test
    void u05ControllerRequiresScene() throws Exception {
        mvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png(10, 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ===== 数据构造 =====

    private FileEntity storeFile(Long createdBy, Long farmId, int bound) {
        FileEntity file = new FileEntity();
        file.setId(900L + files.size());
        file.setBucket("zmt-private");
        file.setObjectKey("inspection/20261019/" + file.getId() + ".jpg");
        file.setFileKind("IMAGE");
        file.setScene("INSPECTION");
        file.setMimeType("image/jpeg");
        file.setFileSize(2048L);
        file.setUploadStatus("READY");
        file.setTranscodeStatus("NONE");
        file.setUploaderType("USER");
        file.setClientType("APP_ANDROID");
        file.setCreatedBy(createdBy);
        file.setFarmId(farmId);
        file.setBound(bound);
        files.put(file.getId(), file);
        return file;
    }

    private static BizMedia media(Long fileId, Long farmId) {
        BizMedia row = new BizMedia();
        row.setId(1L);
        row.setBizType("INSPECTION");
        row.setBizId(7001L);
        row.setFileId(fileId);
        row.setFarmId(farmId);
        row.setSortNo(1);
        row.setDeleted(0);
        return row;
    }

    private static FarmContext.FarmMember member(Long farmId, Long userId, FarmRole role) {
        return new FarmContext.FarmMember(farmId, "东山猪场", userId, 5001L, role, java.util.Set.of(),
                FarmStatus.NORMAL);
    }

    static byte[] png(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(60, 120, 200));
            g.fillRect(0, 0, width, height);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] jpeg(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(40, 40, 40));
            g.fillRect(0, 0, width, height);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 最小 MP4 头：4 字节长度 + ftyp + isom，其余字节只用于上传 */
    static byte[] mp4() {
        byte[] head = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        byte[] body = new byte[512];
        byte[] result = new byte[head.length + body.length];
        System.arraycopy(head, 0, result, 0, head.length);
        return result;
    }
}
