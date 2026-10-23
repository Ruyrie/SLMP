package com.zhimu.server.biz.common.controller;

import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.vo.FileVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.web.WebHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * API-U05–U07 用户端文件（管理端-04 2.5.10）。
 * <p>
 * U05 不挂 @RequireFarm：没有 X-Farm-Id 也能上传（F02 场景）；FARM_QUAL、AI_IMAGE 不校验养殖场状态且 farm_id 存空。
 * multipart 字段名与 App 逐字一致：file、scene、cover、duration_ms、shot_at。同一用户每分钟 60 个。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final DateTimeFormatter SHOT_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

    private final FileService fileService;

    @PostMapping
    @Idempotent
    @RateLimit(key = "file-upload", limit = 60, windowSeconds = 60, by = RateLimit.LimitBy.USER)
    public ApiResponse<FileVO> upload(@RequestPart("file") MultipartFile file,
                                      @RequestParam("scene") String scene,
                                      @RequestPart(value = "cover", required = false) MultipartFile cover,
                                      @RequestParam(value = "duration_ms", required = false) Long durationMs,
                                      @RequestParam(value = "shot_at", required = false) String shotAt,
                                      @RequestHeader(value = WebHeaders.FARM_ID, required = false) Long farmId) {
        FileScene fileScene = CodeEnum.of(FileScene.class, scene);
        if (fileScene == null) {
            throw CommonErrors.fieldError("scene", "不支持的上传场景");
        }
        return ApiResponse.ok(fileService.upload(file, fileScene, cover, durationMs, parseShotAt(shotAt), farmId));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<FileVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(fileService.get(id, com.zhimu.server.common.context.UserContext.requireUserId()));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        fileService.delete(id, com.zhimu.server.common.context.UserContext.requireUserId());
        return ApiResponse.ok();
    }

    /** shot_at 只在视频路径使用（图片忽略），格式 yyyy-MM-dd HH:mm:ss */
    private static LocalDateTime parseShotAt(String shotAt) {
        if (shotAt == null || shotAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(shotAt.trim(), SHOT_AT_FORMAT);
        } catch (DateTimeParseException e) {
            throw CommonErrors.fieldError("shot_at", "拍摄时间格式不正确");
        }
    }
}
