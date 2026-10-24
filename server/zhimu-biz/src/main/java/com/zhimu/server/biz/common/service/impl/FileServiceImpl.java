package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FileKind;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.enums.VariantCode;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FileVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.integration.media.ImageInfo;
import com.zhimu.server.integration.media.ImageProcessor;
import com.zhimu.server.integration.media.MediaInfo;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 文件上传与访问（API-U05–U07）。
 * <p>
 * 不挂 @RequireFarm：没有 X-Farm-Id 也能上传；FARM_QUAL、AI_IMAGE 不校验养殖场状态且 farm_id 存空。
 * 图片 JPG / PNG ≤5MB 并生成长边 360px 的缩略图（t_file_variant THUMB）；视频 MP4 ≤30 秒、≤30MB，
 * App 已压缩的视频直接 READY，封面另存一条 scene = VIDEO_COVER 的 t_file 并由原视频 cover_file_id 指向。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** API-U05 开放的上传场景（管理端-04 2.5.10） */
    public static final Set<FileScene> API_SCENES = EnumSet.of(FileScene.FARM_QUAL, FileScene.AVATAR,
            FileScene.INSPECTION, FileScene.ABNORMAL, FileScene.DISINFECT, FileScene.DEATH, FileScene.VOUCHER,
            FileScene.AI_IMAGE);

    /** FARM_QUAL、AI_IMAGE 不校验养殖场状态且 farm_id 存空 */
    private static final Set<FileScene> NO_FARM_SCENES = EnumSet.of(FileScene.FARM_QUAL, FileScene.AI_IMAGE);

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 30L * 1024 * 1024;
    public static final long MAX_VIDEO_MS = 30_000L;

    private static final int HEAD_BYTES = 16;

    private final FileMapper fileMapper;
    private final FileVariantMapper fileVariantMapper;
    private final BizMediaMapper bizMediaMapper;
    private final ObjectStorage objectStorage;
    private final MediaProcessor mediaProcessor;
    private final ImageProcessor imageProcessor;
    private final FileVoAssembler fileVoAssembler;
    private final FarmMemberCache farmMemberCache;

    @Override
    @Transactional
    public FileVO upload(MultipartFile file, FileScene scene, MultipartFile cover, Long durationMs,
                         LocalDateTime shotAt, Long farmId) {
        if (file == null || file.isEmpty()) {
            throw CommonErrors.fieldError("file", "请选择文件");
        }
        if (scene == null || !API_SCENES.contains(scene)) {
            throw CommonErrors.fieldError("scene", "不支持的上传场景");
        }
        Long userId = UserContext.requireUserId();
        Long effectiveFarmId = NO_FARM_SCENES.contains(scene) ? null : farmId;
        String clientType = clientType();
        byte[] head = readHead(file);
        if (isJpeg(head) || isPng(head)) {
            return uploadImage(file, scene, effectiveFarmId, clientType);
        }
        if (isMp4(head)) {
            return uploadVideo(file, scene, cover, durationMs, shotAt, effectiveFarmId, clientType);
        }
        throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG、PNG 图片或 MP4 视频");
    }

    private FileVO uploadImage(MultipartFile file, FileScene scene, Long farmId, String clientType) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG 或 PNG 图片，单张不超过 5MB");
        }
        byte[] data = readBytes(file);
        ImageInfo info = imageProcessor.readInfo(data);
        String ext = "PNG".equals(info.format()) ? "png" : "jpg";
        String bucket = objectStorage.bucketOf(scene);
        String objectKey = objectStorage.buildObjectKey(scene, ext);
        objectStorage.put(bucket, objectKey, new ByteArrayInputStream(data), data.length, info.mimeType());

        FileEntity entity = new FileEntity();
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setOriginalName(truncate(file.getOriginalFilename(), 255));
        entity.setFileKind(FileKind.IMAGE.name());
        entity.setScene(scene.name());
        entity.setMimeType(info.mimeType());
        entity.setFileExt(ext);
        entity.setFileSize((long) data.length);
        entity.setSha256(sha256(data));
        entity.setWidth(info.width());
        entity.setHeight(info.height());
        entity.setUploadStatus(UploadStatus.READY.name());
        entity.setTranscodeStatus(TranscodeStatus.NONE.name());
        entity.setFarmId(farmId);
        entity.setUploaderType("USER");
        entity.setClientType(clientType);
        entity.setShotAt(null);
        entity.setBound(0);
        fileMapper.insert(entity);

        saveThumbnail(entity, data, scene);
        return fileVoAssembler.toFileVo(entity);
    }

    /** 长边 360px 的 JPEG 缩略图，写 t_file_variant（THUMB） */
    private void saveThumbnail(FileEntity file, byte[] data, FileScene scene) {
        try {
            byte[] thumb = imageProcessor.thumbnail(data);
            String bucket = file.getBucket();
            String objectKey = objectStorage.buildObjectKey(scene, "jpg");
            objectStorage.put(bucket, objectKey, new ByteArrayInputStream(thumb), thumb.length, "image/jpeg");
            FileVariant variant = new FileVariant();
            variant.setFileId(file.getId());
            variant.setVariantCode(VariantCode.THUMB.name());
            variant.setObjectKey(objectKey);
            variant.setStatus(TranscodeStatus.SUCCESS.name());
            variant.setFileSize((long) thumb.length);
            variant.setWidth(null);
            variant.setHeight(null);
            variant.setProgress(java.math.BigDecimal.ONE);
            variant.setRetryCount(0);
            variant.setStartedAt(BeijingTime.now());
            variant.setFinishedAt(BeijingTime.now());
            fileVariantMapper.insert(variant);
        } catch (BizException e) {
            log.warn("生成缩略图失败 file_id={}，上传继续", file.getId(), e);
        }
    }

    private FileVO uploadVideo(MultipartFile file, FileScene scene, MultipartFile cover, Long durationMs,
                               LocalDateTime shotAt, Long farmId, String clientType) {
        if (!scene.isVideoAllowed()) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG 或 PNG 图片，单张不超过 5MB");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BizException(ErrorCode.VIDEO_TOO_LARGE, "视频不能超过 30MB");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("zmt-upload-", ".mp4");
            file.transferTo(temp);
            MediaInfo media;
            try {
                media = mediaProcessor.probe(temp);
            } catch (BizException e) {
                throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG、PNG 图片或 MP4 视频");
            }
            long effectiveMs = media.durationMs() > 0 ? media.durationMs()
                    : durationMs != null ? durationMs : 0L;
            if (effectiveMs > MAX_VIDEO_MS) {
                throw new BizException(ErrorCode.VIDEO_TOO_LARGE, "视频不能超过 30 秒");
            }
            String bucket = objectStorage.bucketOf(scene);
            String objectKey = objectStorage.buildObjectKey(scene, "mp4");
            objectStorage.putFile(bucket, objectKey, temp, "video/mp4");

            FileEntity entity = new FileEntity();
            entity.setBucket(bucket);
            entity.setObjectKey(objectKey);
            entity.setOriginalName(truncate(file.getOriginalFilename(), 255));
            entity.setFileKind(FileKind.VIDEO.name());
            entity.setScene(scene.name());
            entity.setMimeType("video/mp4");
            entity.setFileExt("mp4");
            entity.setFileSize(file.getSize());
            entity.setSha256(sha256(temp));
            entity.setWidth(media.width() > 0 ? media.width() : null);
            entity.setHeight(media.height() > 0 ? media.height() : null);
            entity.setDurationMs((int) Math.min(Integer.MAX_VALUE, effectiveMs));
            entity.setUploadStatus(UploadStatus.READY.name());
            entity.setTranscodeStatus(TranscodeStatus.NONE.name());
            entity.setFarmId(farmId);
            entity.setUploaderType("USER");
            entity.setClientType(clientType);
            entity.setShotAt(shotAt != null ? shotAt : BeijingTime.now());
            entity.setBound(0);
            fileMapper.insert(entity);

            FileEntity coverFile = storeCover(temp, cover, scene, entity.getId(), farmId, clientType);
            if (coverFile != null) {
                entity.setCoverFileId(coverFile.getId());
                fileMapper.updateById(entity);
            }
            Map<Long, FileEntity> covers = new LinkedHashMap<>();
            if (coverFile != null) {
                covers.put(coverFile.getId(), coverFile);
            }
            return fileVoAssembler.toFileVo(entity, covers);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "文件保存失败，请稍后重试");
        } finally {
            deleteQuietly(temp);
        }
    }

    /** 视频封面：优先用 App 随视频上传的 JPG；没有时用 FFmpeg 截第 1 秒画面。失败时保留视频、不阻止上传。 */
    private FileEntity storeCover(Path video, MultipartFile cover, FileScene scene, Long videoId,
                                  Long farmId, String clientType) {
        try {
            byte[] data;
            String ext;
            String mime;
            if (cover != null && !cover.isEmpty()) {
                data = readBytes(cover);
                ImageInfo info = imageProcessor.readInfo(data);
                ext = "PNG".equals(info.format()) ? "png" : "jpg";
                mime = info.mimeType();
            } else {
                Path jpg = Files.createTempFile("zmt-cover-", ".jpg");
                try {
                    mediaProcessor.extractFrame(video, 1.0, jpg);
                    data = Files.readAllBytes(jpg);
                } finally {
                    deleteQuietly(jpg);
                }
                ext = "jpg";
                mime = "image/jpeg";
            }
            FileScene coverScene = FileScene.VIDEO_COVER;
            String bucket = objectStorage.bucketOf(coverScene);
            String objectKey = objectStorage.buildObjectKey(coverScene, ext);
            objectStorage.put(bucket, objectKey, new ByteArrayInputStream(data), data.length, mime);

            FileEntity coverFile = new FileEntity();
            coverFile.setBucket(bucket);
            coverFile.setObjectKey(objectKey);
            coverFile.setOriginalName(cover == null ? null : truncate(cover.getOriginalFilename(), 255));
            coverFile.setFileKind(FileKind.IMAGE.name());
            coverFile.setScene(coverScene.name());
            coverFile.setMimeType(mime);
            coverFile.setFileExt(ext);
            coverFile.setFileSize((long) data.length);
            coverFile.setSha256(sha256(data));
            coverFile.setParentFileId(videoId);
            coverFile.setUploadStatus(UploadStatus.READY.name());
            coverFile.setTranscodeStatus(TranscodeStatus.NONE.name());
            coverFile.setFarmId(farmId);
            coverFile.setUploaderType("USER");
            coverFile.setClientType(clientType);
            coverFile.setBound(1);
            fileMapper.insert(coverFile);
            return coverFile;
        } catch (IOException | RuntimeException e) {
            log.warn("视频封面处理失败 video_id={}，视频仍然可用", videoId, e);
            return null;
        }
    }

    @Override
    public FileVO get(Long id, Long userId) {
        FileEntity file = id == null ? null : fileMapper.selectById(id);
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!canView(file, userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return fileVoAssembler.toFileVo(file);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        FileEntity file = id == null ? null : fileMapper.selectById(id);
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (userId == null || !userId.equals(file.getCreatedBy())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        boolean bound = (file.getBound() != null && file.getBound() == 1)
                || TenantSkip.call(() -> bizMediaMapper.countActiveByFile(id)) > 0;
        if (bound) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个文件已用在记录中，请在记录里删除");
        }
        removeObject(file.getBucket(), file.getObjectKey());
        FileVariant thumb = fileVariantMapper.selectOneByCode(id, VariantCode.THUMB.name());
        if (thumb != null) {
            removeObject(file.getBucket(), thumb.getObjectKey());
        }
        if (file.getCoverFileId() != null) {
            FileEntity cover = fileMapper.selectById(file.getCoverFileId());
            if (cover != null && Objects.equals(cover.getParentFileId(), file.getId())) {
                removeObject(cover.getBucket(), cover.getObjectKey());
                fileMapper.deleteById(cover.getId());
            }
        }
        fileVariantMapper.deleteByFile(id);
        fileMapper.deleteById(id);
    }

    @Override
    public FileEntity find(Long id) {
        return id == null ? null : fileMapper.selectById(id);
    }

    @Override
    public FileVO fileVo(Long id) {
        return fileVoAssembler.toFileVo(find(id));
    }

    /** 本人上传的文件，或文件已关联到本人可查看养殖场的记录 */
    private boolean canView(FileEntity file, Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(file.getCreatedBy())) {
            return true;
        }
        List<BizMedia> rows = TenantSkip.call(() -> bizMediaMapper.selectActiveByFile(file.getId()));
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (BizMedia row : rows) {
            if (row.getFarmId() == null) {
                // 没有养殖场归属（例如资质图尚未通过审核）：登录用户可查看
                return true;
            }
            if (farmMemberCache.get(row.getFarmId(), userId) != null) {
                return true;
            }
        }
        return false;
    }

    private void removeObject(String bucket, String objectKey) {
        if (bucket == null || objectKey == null) {
            return;
        }
        try {
            objectStorage.remove(bucket, objectKey);
        } catch (RuntimeException e) {
            log.warn("删除对象失败 bucket={} key={}", bucket, objectKey, e);
        }
    }

    private static String clientType() {
        ClientType type = RequestContext.clientType();
        return type == null ? ClientType.APP_ANDROID.name() : type.name();
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件读取失败，请重新选择");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件读取失败，请重新选择");
        }
    }

    private static boolean isJpeg(byte[] head) {
        return head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] head) {
        return head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A;
    }

    /** MP4：第 5–8 字节为 ftyp（部分设备为 moov / free，这里按容器 box 判断） */
    private static boolean isMp4(byte[] head) {
        if (head.length < 8) {
            return false;
        }
        String box = new String(head, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return "ftyp".equals(box) || "moov".equals(box) || "free".equals(box) || "mdat".equals(box);
    }

    private static String sha256(byte[] data) {
        return sha256Digest(data == null ? new byte[0] : data);
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String sha256Digest(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("临时文件删除失败 {}", path, e);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** U05 场景校验与文件用途判断用（供测试与 Controller 复用） */
    public static boolean apiScene(FileScene scene) {
        return scene != null && API_SCENES.contains(scene);
    }
}
