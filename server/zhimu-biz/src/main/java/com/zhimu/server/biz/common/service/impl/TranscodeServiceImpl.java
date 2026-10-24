package com.zhimu.server.biz.common.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.event.FileTranscodeFinishedEvent;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.enums.FileKind;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.enums.VariantCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.integration.media.HlsVariant;
import com.zhimu.server.integration.media.MediaConfig;
import com.zhimu.server.integration.media.MediaException;
import com.zhimu.server.integration.media.MediaInfo;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 课程视频转码：先 720P 后 480P，每段 6 秒；原片成功后保留 7 天，HLS 永久保留。
 * 任一档失败按 1、5、15 分钟自动重试 3 次，仍失败置 FAILED 并发布 FileTranscodeFinishedEvent。
 */
@Slf4j
@Service
public class TranscodeServiceImpl implements TranscodeService {

    private static final int MAX_RETRY = 3;
    private static final long[] RETRY_INTERVAL_MS = {60_000L, 5 * 60_000L, 15 * 60_000L};
    private static final long STALE_PROCESSING_MS = 10 * 60_000L;
    private static final long REQUEST_RECOVERY_MS = 5 * 60_000L;

    private final FileMapper fileMapper;
    private final FileVariantMapper variantMapper;
    private final ObjectStorage objectStorage;
    private final MediaProcessor mediaProcessor;
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Long, Long> lastProgressAt = new ConcurrentHashMap<>();

    public TranscodeServiceImpl(FileMapper fileMapper, FileVariantMapper variantMapper,
                                ObjectStorage objectStorage, MediaProcessor mediaProcessor,
                                ApplicationEventPublisher eventPublisher,
                                @Qualifier(MediaConfig.TRANSCODE_EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.fileMapper = fileMapper;
        this.variantMapper = variantMapper;
        this.objectStorage = objectStorage;
        this.mediaProcessor = mediaProcessor;
        this.eventPublisher = eventPublisher;
        this.executor = executor;
    }

    @Override
    public void submit(Long fileId) {
        if (fileId == null) {
            return;
        }
        try {
            executor.execute(() -> run(fileId));
        } catch (RuntimeException e) {
            log.warn("提交转码任务失败 file_id={}", fileId, e);
        }
    }

    @Override
    public void retry(Long fileId) {
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null) {
            return;
        }
        for (FileVariant variant : variants(fileId)) {
            variant.setStatus(TranscodeStatus.WAITING.name());
            variant.setProgress(BigDecimal.ZERO);
            variant.setErrorMessage(null);
            variant.setRetryCount(0);
            variant.setStartedAt(null);
            variant.setFinishedAt(null);
            variantMapper.updateById(variant);
        }
        file.setTranscodeStatus(TranscodeStatus.WAITING.name());
        file.setTranscodeError(null);
        fileMapper.updateById(file);
        AfterCommit.run(() -> submit(fileId));
    }

    @Override
    public void dispatch() {
        LocalDateTime now = BeijingTime.now();
        List<FileEntity> waiting = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                .eq(FileEntity::getTranscodeStatus, TranscodeStatus.WAITING.name()));
        for (FileEntity file : waiting) {
            List<FileVariant> variants = variants(file.getId());
            if (variants.isEmpty()) {
                submit(file.getId());
                continue;
            }
            int retry = variants.stream().mapToInt(v -> v.getRetryCount() == null ? 0 : v.getRetryCount()).max().orElse(0);
            long waitMs = retry <= 0 ? REQUEST_RECOVERY_MS
                    : RETRY_INTERVAL_MS[Math.min(retry, MAX_RETRY) - 1];
            LocalDateTime updated = file.getUpdatedAt() == null ? now : file.getUpdatedAt();
            if (updated.plusNanos(waitMs * 1_000_000L).isBefore(now)) {
                submit(file.getId());
            }
        }
        List<FileEntity> processing = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                .eq(FileEntity::getTranscodeStatus, TranscodeStatus.PROCESSING.name()));
        for (FileEntity file : processing) {
            LocalDateTime updated = file.getUpdatedAt() == null ? now : file.getUpdatedAt();
            if (updated.plusNanos(STALE_PROCESSING_MS * 1_000_000L).isBefore(now)) {
                for (FileVariant variant : variants(file.getId())) {
                    variant.setStatus(TranscodeStatus.WAITING.name());
                    variant.setProgress(BigDecimal.ZERO);
                    variant.setStartedAt(null);
                    variantMapper.updateById(variant);
                }
                file.setTranscodeStatus(TranscodeStatus.WAITING.name());
                fileMapper.updateById(file);
                submit(file.getId());
            }
        }
    }

    private void run(Long fileId) {
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null || !UploadStatus.READY.name().equals(file.getUploadStatus())) {
            return;
        }
        int claimed = fileMapper.update(null, Wrappers.<FileEntity>lambdaUpdate()
                .eq(FileEntity::getId, fileId)
                .eq(FileEntity::getTranscodeStatus, TranscodeStatus.WAITING.name())
                .set(FileEntity::getTranscodeStatus, TranscodeStatus.PROCESSING.name())
                .set(FileEntity::getUpdatedAt, BeijingTime.now()));
        if (claimed == 0) {
            return;
        }
        List<FileVariant> variants = ensureVariants(file);
        markProcessing(variants);
        Path workDir = null;
        boolean rawMissing = false;
        try {
            workDir = Files.createTempDirectory("zmt-transcode-" + fileId + "-");
            Path raw = workDir.resolve("raw.bin");
            try (InputStream in = objectStorage.get(file.getBucket(), file.getObjectKey())) {
                Files.copy(in, raw, StandardCopyOption.REPLACE_EXISTING);
            } catch (com.zhimu.server.common.error.BizException e) {
                rawMissing = true;
                throw e;
            }
            MediaInfo info = mediaProcessor.probe(raw);
            if (info.durationMs() > 30L * 60 * 1000 + 500) {
                throw new MediaException("video too long", "duration_ms=" + info.durationMs());
            }
            Path hlsDir = workDir.resolve("hls");
            Files.createDirectories(hlsDir);
            mediaProcessor.transcodeHls(raw, hlsDir, List.of(HlsVariant.P720, HlsVariant.P480),
                    (variant, progress) -> onProgress(fileId, variant, progress));

            Map<String, Long> sizes = uploadHls(fileId, hlsDir);
            uploadMaster(fileId, info);
            List<FileEntity> covers = createCovers(fileId, raw, workDir.resolve("covers"));

            for (FileVariant variant : variants) {
                variant.setStatus(TranscodeStatus.SUCCESS.name());
                variant.setProgress(BigDecimal.ONE);
                variant.setErrorMessage(null);
                variant.setFinishedAt(BeijingTime.now());
                variant.setStartedAt(variant.getStartedAt() == null ? BeijingTime.now() : variant.getStartedAt());
                String code = "HLS_720P".equals(variant.getVariantCode()) ? "720p" : "480p";
                variant.setObjectKey(objectStorage.hlsObjectPrefix(fileId) + code + "/index.m3u8");
                variant.setFileSize(sizes.getOrDefault(variant.getVariantCode(), 0L));
                if ("HLS_720P".equals(variant.getVariantCode())) {
                    variant.setWidth(Math.min(1280, Math.max(1, info.width())));
                    variant.setHeight(Math.min(720, Math.max(1, info.height())));
                    variant.setBitrateKbps(1500);
                } else {
                    variant.setWidth(Math.min(854, Math.max(1, info.width())));
                    variant.setHeight(Math.min(480, Math.max(1, info.height())));
                    variant.setBitrateKbps(800);
                }
                variantMapper.updateById(variant);
            }

            file.setTranscodeStatus(TranscodeStatus.SUCCESS.name());
            file.setTranscodeError(null);
            file.setDurationMs((int) Math.min(Integer.MAX_VALUE, info.durationMs()));
            file.setWidth(info.width() > 0 ? info.width() : null);
            file.setHeight(info.height() > 0 ? info.height() : null);
            file.setCoverFileId(covers.isEmpty() ? null : covers.get(0).getId());
            file.setExpireAt(BeijingTime.now().plusDays(7));
            fileMapper.updateById(file);
            eventPublisher.publishEvent(new FileTranscodeFinishedEvent(fileId, true, null));
            log.info("转码完成 file_id={} duration_ms={}", fileId, info.durationMs());
        } catch (Exception e) {
            String reason = reasonOf(e);
            log.warn("转码失败 file_id={}：{}", fileId, reason, e);
            handleFailure(fileId, reason, rawMissing);
        } finally {
            deleteTree(workDir);
            lastProgressAt.remove(fileId);
        }
    }

    private List<FileVariant> ensureVariants(FileEntity file) {
        List<FileVariant> variants = variants(file.getId());
        if (variants.size() >= 2) {
            return variants;
        }
        for (VariantCode code : List.of(VariantCode.HLS_720P, VariantCode.HLS_480P)) {
            boolean exists = variants.stream().anyMatch(v -> code.name().equals(v.getVariantCode()));
            if (exists) {
                continue;
            }
            FileVariant variant = new FileVariant();
            variant.setFileId(file.getId());
            variant.setVariantCode(code.name());
            variant.setStatus(TranscodeStatus.WAITING.name());
            variant.setProgress(BigDecimal.ZERO);
            variant.setWidth(code == VariantCode.HLS_720P ? 1280 : 854);
            variant.setHeight(code == VariantCode.HLS_720P ? 720 : 480);
            variant.setRetryCount(0);
            variantMapper.insert(variant);
            variants = new ArrayList<>(variants);
            variants.add(variant);
        }
        variants.sort(Comparator.comparing(FileVariant::getVariantCode));
        return variants;
    }

    private void markProcessing(List<FileVariant> variants) {
        for (FileVariant variant : variants) {
            variant.setStatus(TranscodeStatus.PROCESSING.name());
            variant.setProgress(BigDecimal.ZERO);
            variant.setErrorMessage(null);
            variant.setStartedAt(BeijingTime.now());
            variant.setFinishedAt(null);
            variantMapper.updateById(variant);
        }
    }

    private void onProgress(Long fileId, HlsVariant hls, double progress) {
        long now = System.currentTimeMillis();
        Long last = lastProgressAt.get(fileId);
        if (last != null && now - last < 5_000) {
            return;
        }
        lastProgressAt.put(fileId, now);
        String code = hls == HlsVariant.P720 ? VariantCode.HLS_720P.name() : VariantCode.HLS_480P.name();
        FileVariant variant = variantMapper.selectOneByCode(fileId, code);
        if (variant == null || !TranscodeStatus.PROCESSING.name().equals(variant.getStatus())) {
            return;
        }
        variant.setProgress(BigDecimal.valueOf(Math.max(0, Math.min(1, progress)))
                .setScale(4, RoundingMode.HALF_UP));
        variantMapper.updateById(variant);
    }

    /** 上传 hls/{fileId}/720p|480p 下的全部文件，返回各档合计大小 */
    private Map<String, Long> uploadHls(Long fileId, Path hlsDir) throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        try (var stream = Files.walk(hlsDir)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relative = hlsDir.relativize(path).toString().replace('\\', '/');
                String objectKey = objectStorage.hlsObjectPrefix(fileId) + relative;
                String contentType = relative.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
                objectStorage.putFile(objectStorage.courseBucket(), objectKey, path, contentType);
                long size = Files.size(path);
                if (relative.startsWith("720p/")) {
                    sizes.merge(VariantCode.HLS_720P.name(), size, Long::sum);
                } else if (relative.startsWith("480p/")) {
                    sizes.merge(VariantCode.HLS_480P.name(), size, Long::sum);
                }
            }
        }
        return sizes;
    }

    private void uploadMaster(Long fileId, MediaInfo info) {
        int width720 = Math.min(1280, Math.max(1, info.width()));
        int height720 = Math.min(720, Math.max(1, info.height()));
        int width480 = Math.min(854, Math.max(1, info.width()));
        int height480 = Math.min(480, Math.max(1, info.height()));
        String master = "#EXTM3U\n#EXT-X-VERSION:3\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1600000,RESOLUTION=" + width720 + "x" + height720 + ",NAME=\"720P\"\n"
                + "720p/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=" + width480 + "x" + height480 + ",NAME=\"480P\"\n"
                + "480p/index.m3u8\n";
        byte[] data = master.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        objectStorage.put(objectStorage.courseBucket(), objectStorage.hlsObjectPrefix(fileId) + "index.m3u8",
                new java.io.ByteArrayInputStream(data), data.length, "application/vnd.apple.mpegurl");
    }

    private List<FileEntity> createCovers(Long fileId, Path raw, Path coverDir) {
        List<FileEntity> result = new ArrayList<>();
        try {
            Files.createDirectories(coverDir);
            List<Path> covers = mediaProcessor.coverCandidates(raw, 3, coverDir);
            int index = 1;
            for (Path cover : covers) {
                String objectKey = "cover/" + fileId + "/" + index + ".jpg";
                objectStorage.putFile(objectStorage.courseBucket(), objectKey, cover, "image/jpeg");
                FileEntity entity = new FileEntity();
                entity.setBucket(objectStorage.courseBucket());
                entity.setObjectKey(objectKey);
                entity.setOriginalName(fileId + "-cover-" + index + ".jpg");
                entity.setFileKind(FileKind.IMAGE.name());
                entity.setScene(FileScene.VIDEO_COVER.name());
                entity.setMimeType("image/jpeg");
                entity.setFileExt("jpg");
                entity.setFileSize(Files.size(cover));
                entity.setWidth(1280);
                entity.setHeight(720);
                entity.setParentFileId(fileId);
                entity.setUploadStatus(UploadStatus.READY.name());
                entity.setTranscodeStatus(TranscodeStatus.NONE.name());
                entity.setUploaderType("SYSTEM");
                entity.setBound(0);
                fileMapper.insert(entity);
                result.add(entity);
                index++;
            }
        } catch (Exception e) {
            log.warn("生成封面候选失败 file_id={}", fileId, e);
        }
        return result;
    }

    private void handleFailure(Long fileId, String reason, boolean rawMissing) {
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null) {
            return;
        }
        List<FileVariant> variants = variants(fileId);
        int retry = variants.stream().mapToInt(v -> v.getRetryCount() == null ? 0 : v.getRetryCount())
                .max().orElse(0);
        boolean finalFailure = rawMissing || retry >= MAX_RETRY;
        for (FileVariant variant : variants) {
            variant.setProgress(BigDecimal.ZERO);
            variant.setErrorMessage(truncate(reason, 500));
            variant.setFinishedAt(BeijingTime.now());
            if (finalFailure) {
                variant.setStatus(TranscodeStatus.FAILED.name());
            } else {
                variant.setStatus(TranscodeStatus.WAITING.name());
                variant.setRetryCount(retry + 1);
                variant.setStartedAt(null);
            }
            variantMapper.updateById(variant);
        }
        file.setTranscodeStatus(finalFailure ? TranscodeStatus.FAILED.name() : TranscodeStatus.WAITING.name());
        file.setTranscodeError(truncate(reason, 500));
        fileMapper.updateById(file);
        if (finalFailure) {
            eventPublisher.publishEvent(new FileTranscodeFinishedEvent(fileId, false, reason));
        }
    }

    private List<FileVariant> variants(Long fileId) {
        return variantMapper.selectList(Wrappers.<FileVariant>lambdaQuery()
                .eq(FileVariant::getFileId, fileId)
                .orderByAsc(FileVariant::getId));
    }

    private static String reasonOf(Exception e) {
        if (e instanceof MediaException media) {
            String detail = media.getDetail();
            return media.getMessage() + (detail == null || detail.isBlank() ? "" : "：" + truncate(detail, 400));
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : truncate(message, 500);
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响业务
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
