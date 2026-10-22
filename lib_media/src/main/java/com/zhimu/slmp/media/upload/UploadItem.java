package com.zhimu.slmp.media.upload;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.FileVO;

import java.util.Objects;
import java.util.UUID;

/**
 * 照片网格中的一项（不可变，状态变化返回新对象）。
 * <pre>
 * WAITING → COMPRESSING → UPLOADING(progress) → DONE(FileVO)
 *    └──────────┴───────────────┴──→ FAILED(error) ──retry──→ WAITING
 * </pre>
 * 编辑模式从服务端带入的文件为 {@link #remote} 项，状态直接为 DONE，删除时不调用 API-U07。
 * id 同时作为 API-U05 的幂等键。实现 Parcelable，页面重建（含进程被回收）后由 UploadManager 从 SavedStateHandle 恢复。
 */
public final class UploadItem implements Parcelable {

    public enum Status {
        WAITING, COMPRESSING, UPLOADING, DONE, FAILED
    }

    public static final String MEDIA_IMAGE = "IMAGE";
    public static final String MEDIA_VIDEO = "VIDEO";

    @NonNull
    private String id;
    @NonNull
    private Status status;
    /** 0–100，只在 UPLOADING 时有意义 */
    private int progress;
    /** 来源：content:// 或 file:// 地址（remote 项为 null） */
    @Nullable
    private String sourceUri;
    /** 压缩后的本地文件（重试时不再压缩；预览优先显示本地文件） */
    @Nullable
    private String localPath;
    private long fileId;
    @Nullable
    private String url;
    @Nullable
    private String thumbUrl;
    @NonNull
    private String mediaType = MEDIA_IMAGE;
    @Nullable
    private Integer durationSec;
    /** 失败原因（可显示的文案） */
    @Nullable
    private String error;
    /** 左下角标签，如“新增”“需重拍”；类型为 StatusTagView 的 TagType */
    @Nullable
    private String tagText;
    @StatusTagView.TagType
    private int tagType;
    private boolean remote;

    private UploadItem(@NonNull String id, @NonNull Status status) {
        this.id = id;
        this.status = status;
    }

    // ===== 创建 =====

    /** 新选择或新拍摄的本地文件，等待压缩上传 */
    @NonNull
    public static UploadItem newLocal(@NonNull String sourceUri) {
        UploadItem item = new UploadItem(UUID.randomUUID().toString(), Status.WAITING);
        item.sourceUri = sourceUri;
        return item;
    }

    /** 服务端已有的文件（编辑模式带入、详情只读展示） */
    @NonNull
    public static UploadItem remote(long fileId, @Nullable String url, @Nullable String thumbUrl) {
        UploadItem item = new UploadItem("remote_" + fileId + "_" + UUID.randomUUID(), Status.DONE);
        item.fileId = fileId;
        item.url = url;
        item.thumbUrl = thumbUrl;
        item.progress = 100;
        item.remote = true;
        return item;
    }

    /** 由 FileVO（或 MediaItemVO）创建 remote 项 */
    @NonNull
    public static UploadItem remote(@NonNull FileVO vo) {
        UploadItem item = remote(vo.getFileId(), vo.getUrl(), vo.getThumbUrl());
        if (vo.getMediaType() != null) {
            item.mediaType = vo.getMediaType();
        }
        item.durationSec = vo.getDurationSec();
        return item;
    }

    // ===== 状态流转 =====

    /** 允许的状态变化（同状态只允许 UPLOADING 更新进度） */
    public static boolean canTransit(@NonNull Status from, @NonNull Status to) {
        switch (from) {
            case WAITING:
                return to == Status.COMPRESSING || to == Status.UPLOADING || to == Status.FAILED;
            case COMPRESSING:
                return to == Status.UPLOADING || to == Status.FAILED || to == Status.WAITING;
            case UPLOADING:
                return to == Status.UPLOADING || to == Status.DONE || to == Status.FAILED || to == Status.WAITING;
            case FAILED:
                return to == Status.WAITING;
            case DONE:
            default:
                return false;
        }
    }

    /** WAITING → COMPRESSING */
    @NonNull
    public UploadItem toCompressing() {
        return moveTo(Status.COMPRESSING);
    }

    /** COMPRESSING（或已压缩过的 WAITING）→ UPLOADING，进度 0 */
    @NonNull
    public UploadItem toUploading(@NonNull String compressedPath) {
        UploadItem next = moveTo(Status.UPLOADING);
        next.localPath = compressedPath;
        next.progress = 0;
        return next;
    }

    /** UPLOADING 中更新进度（0–100）；进度没变时返回自身 */
    @NonNull
    public UploadItem withProgress(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        if (status != Status.UPLOADING) {
            throw new IllegalStateException("只有上传中可以更新进度：" + status);
        }
        if (p == progress) {
            return this;
        }
        UploadItem next = copy();
        next.progress = p;
        return next;
    }

    /** UPLOADING → DONE，写入 file_id 与地址 */
    @NonNull
    public UploadItem toDone(@NonNull FileVO vo) {
        UploadItem next = moveTo(Status.DONE);
        next.progress = 100;
        next.fileId = vo.getFileId();
        next.url = vo.getUrl();
        next.thumbUrl = vo.getThumbUrl();
        if (vo.getMediaType() != null) {
            next.mediaType = vo.getMediaType();
        }
        next.durationSec = vo.getDurationSec();
        next.error = null;
        return next;
    }

    /** WAITING / COMPRESSING / UPLOADING → FAILED */
    @NonNull
    public UploadItem toFailed(@Nullable String reason) {
        UploadItem next = moveTo(Status.FAILED);
        next.error = reason;
        next.progress = 0;
        return next;
    }

    /** FAILED → WAITING（点按重试；已压缩的文件保留，不再压缩） */
    @NonNull
    public UploadItem toRetry() {
        UploadItem next = moveTo(Status.WAITING);
        next.error = null;
        next.progress = 0;
        return next;
    }

    /** 处理中被打断（页面重建后恢复、撤销删除）：COMPRESSING / UPLOADING → WAITING；其他状态原样返回 */
    @NonNull
    public UploadItem toInterrupted() {
        if (status != Status.COMPRESSING && status != Status.UPLOADING) {
            return this;
        }
        UploadItem next = moveTo(Status.WAITING);
        next.progress = 0;
        return next;
    }

    /** 设置左下角标签（如“新增”“需重拍”），type 为 StatusTagView 的 TagType */
    @NonNull
    public UploadItem withTag(@Nullable String text, @StatusTagView.TagType int type) {
        UploadItem next = copy();
        next.tagText = text;
        next.tagType = type;
        return next;
    }

    @NonNull
    private UploadItem moveTo(@NonNull Status to) {
        if (!canTransit(status, to)) {
            throw new IllegalStateException("不允许的状态变化：" + status + " → " + to);
        }
        UploadItem next = copy();
        next.status = to;
        return next;
    }

    @NonNull
    private UploadItem copy() {
        UploadItem c = new UploadItem(id, status);
        c.progress = progress;
        c.sourceUri = sourceUri;
        c.localPath = localPath;
        c.fileId = fileId;
        c.url = url;
        c.thumbUrl = thumbUrl;
        c.mediaType = mediaType;
        c.durationSec = durationSec;
        c.error = error;
        c.tagText = tagText;
        c.tagType = tagType;
        c.remote = remote;
        return c;
    }

    // ===== 查询 =====

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    @Nullable
    public String getSourceUri() {
        return sourceUri;
    }

    @Nullable
    public String getLocalPath() {
        return localPath;
    }

    public long getFileId() {
        return fileId;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getThumbUrl() {
        return thumbUrl;
    }

    @NonNull
    public String getMediaType() {
        return mediaType;
    }

    @Nullable
    public Integer getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public String getTagText() {
        return tagText;
    }

    @StatusTagView.TagType
    public int getTagType() {
        return tagType;
    }

    public boolean isRemote() {
        return remote;
    }

    public boolean isDone() {
        return status == Status.DONE;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /** 等待、压缩或上传中 */
    public boolean isInProgress() {
        return status == Status.WAITING || status == Status.COMPRESSING || status == Status.UPLOADING;
    }

    public boolean isVideo() {
        return MEDIA_VIDEO.equals(mediaType);
    }

    /** 已压缩过（重试时可直接上传） */
    public boolean hasCompressedFile() {
        return localPath != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UploadItem)) {
            return false;
        }
        UploadItem that = (UploadItem) o;
        return progress == that.progress && fileId == that.fileId && tagType == that.tagType
                && remote == that.remote && id.equals(that.id) && status == that.status
                && Objects.equals(sourceUri, that.sourceUri) && Objects.equals(localPath, that.localPath)
                && Objects.equals(url, that.url) && Objects.equals(thumbUrl, that.thumbUrl)
                && mediaType.equals(that.mediaType) && Objects.equals(durationSec, that.durationSec)
                && Objects.equals(error, that.error) && Objects.equals(tagText, that.tagText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, progress, fileId);
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadItem{" + id + ", " + status + ", " + progress + "%, fileId=" + fileId + "}";
    }

    // ===== Parcelable =====

    private UploadItem(@NonNull Parcel in) {
        id = Objects.requireNonNull(in.readString());
        status = Status.valueOf(in.readString());
        progress = in.readInt();
        sourceUri = in.readString();
        localPath = in.readString();
        fileId = in.readLong();
        url = in.readString();
        thumbUrl = in.readString();
        mediaType = Objects.requireNonNull(in.readString());
        int duration = in.readInt();
        durationSec = duration < 0 ? null : duration;
        error = in.readString();
        tagText = in.readString();
        tagType = in.readInt();
        remote = in.readInt() == 1;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(status.name());
        dest.writeInt(progress);
        dest.writeString(sourceUri);
        dest.writeString(localPath);
        dest.writeLong(fileId);
        dest.writeString(url);
        dest.writeString(thumbUrl);
        dest.writeString(mediaType);
        dest.writeInt(durationSec == null ? -1 : durationSec);
        dest.writeString(error);
        dest.writeString(tagText);
        dest.writeInt(tagType);
        dest.writeInt(remote ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UploadItem> CREATOR = new Creator<UploadItem>() {
        @Override
        public UploadItem createFromParcel(Parcel in) {
            return new UploadItem(in);
        }

        @Override
        public UploadItem[] newArray(int size) {
            return new UploadItem[size];
        }
    };
}
