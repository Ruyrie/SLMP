package com.zhimu.slmp.base.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 照片和视频上传队列（upload_task）：记录压缩与上传进度、失败原因和服务端 file_id，页面重建后可恢复缩略图状态。
 * 本期照片直接上传，表结构先建好，队列与后台任务在模块07 时启用。
 */
@Entity(tableName = "upload_task", indices = {@Index(value = {"page_code", "farm_id"})})
public class UploadTaskEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_COMPRESSING = "COMPRESSING";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "local_uri")
    public String localUri = "";

    /** 上传场景 FILE_SCENE，例如 DEATH、FARM_QUAL */
    @NonNull
    @ColumnInfo(name = "scene")
    public String scene = "";

    /** IMAGE / VIDEO / AUDIO */
    @NonNull
    @ColumnInfo(name = "media_type")
    public String mediaType = "";

    @NonNull
    @ColumnInfo(name = "status")
    public String status = STATUS_WAITING;

    /** 0–100 */
    @ColumnInfo(name = "progress")
    public int progress;

    @Nullable
    @ColumnInfo(name = "file_id")
    public Long fileId;

    @Nullable
    @ColumnInfo(name = "error")
    public String error;

    @NonNull
    @ColumnInfo(name = "page_code")
    public String pageCode = "";

    @ColumnInfo(name = "farm_id")
    public long farmId;

    /** 毫秒时间戳 */
    @ColumnInfo(name = "created_at")
    public long createdAt;
}
