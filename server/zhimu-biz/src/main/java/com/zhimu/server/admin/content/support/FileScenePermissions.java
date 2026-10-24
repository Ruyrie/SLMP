package com.zhimu.server.admin.content.support;

import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/**
 * 管理端文件场景与页面权限的映射（管理端-04 1.3 / ADM-S34–S38）。
 * 小文件上传：NOTICE_IMAGE、COURSE_COVER、TOPIC_COVER、KNOWLEDGE_MEDIA；
 * 视频分片：COURSE_VIDEO、KNOWLEDGE_MEDIA。
 */
public final class FileScenePermissions {

    private FileScenePermissions() {
    }

    public static String permissionOf(FileScene scene) {
        return switch (scene) {
            case NOTICE_IMAGE -> "WA-C01";
            case COURSE_VIDEO, COURSE_COVER, TOPIC_COVER -> "WA-C03";
            case KNOWLEDGE_MEDIA -> "WA-C04";
            default -> null;
        };
    }

    public static String handlePermission(FileScene scene) {
        String page = permissionOf(scene);
        return page == null ? null : page + ":HANDLE";
    }

    public static String viewPermission(FileScene scene) {
        String page = permissionOf(scene);
        return page == null ? null : page + ":VIEW";
    }

    public static boolean smallUploadAllowed(FileScene scene) {
        return scene == FileScene.NOTICE_IMAGE || scene == FileScene.COURSE_COVER
                || scene == FileScene.TOPIC_COVER || scene == FileScene.KNOWLEDGE_MEDIA;
    }

    public static boolean multipartAllowed(FileScene scene) {
        return scene == FileScene.COURSE_VIDEO || scene == FileScene.KNOWLEDGE_MEDIA;
    }

    public static FileScene scene(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
        try {
            return FileScene.valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的上传场景");
        }
    }
}
