package com.zhimu.slmp.inspect.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 测试直连的诊断输入（I05 提交时组装，I06 用来调用模型并替换上报信息）：不含姓名、手机号以外的隐私信息，
 * 提示词中不写上报人姓名。
 */
public final class DirectDiagnosisInput {

    /** 一个照片或视频：本地文件优先（压缩或转码后的成品），其次服务端地址 */
    public static final class Media {
        public final boolean video;
        @Nullable
        public final String localPath;
        @Nullable
        public final String url;
        @Nullable
        public final String thumbUrl;
        @Nullable
        public final Integer durationSec;
        public final long fileId;

        public Media(boolean video, @Nullable String localPath, @Nullable String url, @Nullable String thumbUrl,
                     @Nullable Integer durationSec, long fileId) {
            this.video = video;
            this.localPath = localPath;
            this.url = url;
            this.thumbUrl = thumbUrl;
            this.durationSec = durationSec;
            this.fileId = fileId;
        }
    }

    public long penId;
    @NonNull
    public String penName = "";
    public long batchId;
    @NonNull
    public String batchNo = "";
    @Nullable
    public String breedTitle;
    @Nullable
    public Integer dayAge;
    public int stockCount;
    public int sickCount;
    @NonNull
    public List<String> symptomNames = new ArrayList<>();
    @Nullable
    public String description;
    /** INSPECTION 巡检转上报 / DIRECT 直接上报 */
    @NonNull
    public String source = "DIRECT";
    /** 转上报时的巡检结果摘要，例如“粪便 水样”或手动标记原因 */
    @Nullable
    public String inspectionSummary;
    @Nullable
    public Long inspectionId;
    /** 同批次今天第几次上报（未知时为 1） */
    public int sameDayIndex = 1;
    @NonNull
    public String reportedAt = "";
    @Nullable
    public String reporterName;
    @NonNull
    public List<Media> media = new ArrayList<>();
    /** 上报时选择的症状与媒体对应的服务端 file_id（替换 I06 上报信息时使用） */
    @NonNull
    public List<com.zhimu.slmp.common.model.vo.MediaItemVO> mediaVos = Collections.emptyList();

    /** 发病率（四位小数），存栏为 0 时为 0 */
    public double sickRate() {
        return stockCount <= 0 ? 0 : Math.round(sickCount * 10000.0 / stockCount) / 10000.0;
    }

    public int photoCount() {
        int n = 0;
        for (Media m : media) {
            if (!m.video) {
                n++;
            }
        }
        return n;
    }

    public boolean hasVideo() {
        for (Media m : media) {
            if (m.video) {
                return true;
            }
        }
        return false;
    }
}
