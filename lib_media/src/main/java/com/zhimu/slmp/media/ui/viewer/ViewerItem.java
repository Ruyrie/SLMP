package com.zhimu.slmp.media.ui.viewer;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.media.upload.UploadItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全屏预览 I09 的一项：地址（服务端 url、本地文件路径、content:// 或 file:///android_asset/）、file_id 与两行来源说明。
 */
public final class ViewerItem implements Parcelable {

    private final long fileId;
    @Nullable
    private final String url;
    @Nullable
    private final String thumbUrl;
    @NonNull
    private final String mediaType;
    @Nullable
    private final Integer durationSec;
    @Nullable
    private final String sourceLine;
    @Nullable
    private final String detailLine;

    public ViewerItem(long fileId, @Nullable String url, @Nullable String thumbUrl, @Nullable String mediaType,
                      @Nullable Integer durationSec, @Nullable String sourceLine, @Nullable String detailLine) {
        this.fileId = fileId;
        this.url = url;
        this.thumbUrl = thumbUrl;
        this.mediaType = mediaType == null ? UploadItem.MEDIA_IMAGE : mediaType;
        this.durationSec = durationSec;
        this.sourceLine = sourceLine;
        this.detailLine = detailLine;
    }

    /** 本地图片（相机刚拍的、刚选的） */
    @NonNull
    public static ViewerItem local(@NonNull String pathOrUri, @Nullable String sourceLine) {
        return new ViewerItem(0, pathOrUri, null, UploadItem.MEDIA_IMAGE, null, sourceLine, null);
    }

    /**
     * 服务端文件；MediaItemVO 自动生成来源说明：“死淘登记 · 拍摄人李明”“10月19日 09:10 拍摄 · 视频 0:18”。
     *
     * @param fallbackSource FileVO 没有来源字段时的第一行
     */
    @NonNull
    public static ViewerItem from(@NonNull FileVO vo, @Nullable String fallbackSource) {
        String line1 = fallbackSource;
        String line2 = null;
        boolean video = UploadItem.MEDIA_VIDEO.equals(vo.getMediaType());
        if (vo instanceof MediaItemVO) {
            MediaItemVO m = (MediaItemVO) vo;
            List<String> parts = new ArrayList<>();
            String source = !TextUtils.isEmpty(m.getSourceText()) ? m.getSourceText() : fallbackSource;
            if (!TextUtils.isEmpty(source)) {
                parts.add(source);
            }
            if (!TextUtils.isEmpty(m.getShotBy())) {
                parts.add("拍摄人" + m.getShotBy());
            }
            line1 = parts.isEmpty() ? null : TextUtils.join(" · ", parts);
            List<String> detail = new ArrayList<>();
            if (!TextUtils.isEmpty(m.getShotAt())) {
                detail.add(DateFormatter.formatDateTime(m.getShotAt()) + " 拍摄");
            }
            if (video && vo.getDurationSec() != null) {
                detail.add("视频 " + formatDuration(vo.getDurationSec()));
            }
            line2 = detail.isEmpty() ? null : TextUtils.join(" · ", detail);
        }
        return new ViewerItem(vo.getFileId(), vo.getUrl(), vo.getThumbUrl(), vo.getMediaType(),
                vo.getDurationSec(), line1, line2);
    }

    /** 照片网格中的一项：本地文件优先（上传中也能预览），其次服务端地址 */
    @NonNull
    public static ViewerItem from(@NonNull UploadItem item, @Nullable String sourceLine) {
        String url = item.getUrl();
        String local = item.getLocalPath();
        if (local != null && new File(local).isFile()) {
            url = local;
        } else if (!item.isRemote() && item.getSourceUri() != null) {
            url = item.getSourceUri();
        }
        return new ViewerItem(item.getFileId(), url, item.getThumbUrl(), item.getMediaType(),
                item.getDurationSec(), sourceLine, null);
    }

    /** “0:18” */
    @NonNull
    public static String formatDuration(int seconds) {
        int s = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** API-U06 换到新的临时地址后 */
    @NonNull
    ViewerItem withUrl(@Nullable String newUrl) {
        return new ViewerItem(fileId, newUrl, thumbUrl, mediaType, durationSec, sourceLine, detailLine);
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

    public boolean isVideo() {
        return UploadItem.MEDIA_VIDEO.equals(mediaType);
    }

    @Nullable
    public Integer getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getSourceLine() {
        return sourceLine;
    }

    @Nullable
    public String getDetailLine() {
        return detailLine;
    }

    private ViewerItem(@NonNull Parcel in) {
        fileId = in.readLong();
        url = in.readString();
        thumbUrl = in.readString();
        String type = in.readString();
        mediaType = type == null ? UploadItem.MEDIA_IMAGE : type;
        int d = in.readInt();
        durationSec = d < 0 ? null : d;
        sourceLine = in.readString();
        detailLine = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(fileId);
        dest.writeString(url);
        dest.writeString(thumbUrl);
        dest.writeString(mediaType);
        dest.writeInt(durationSec == null ? -1 : durationSec);
        dest.writeString(sourceLine);
        dest.writeString(detailLine);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ViewerItem> CREATOR = new Creator<ViewerItem>() {
        @Override
        public ViewerItem createFromParcel(Parcel in) {
            return new ViewerItem(in);
        }

        @Override
        public ViewerItem[] newArray(int size) {
            return new ViewerItem[size];
        }
    };
}
