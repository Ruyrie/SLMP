package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

/**
 * API-E42 导出结果
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReportExportVO {

    private long exportId;
    private long fileId;
    private String fileName;
    private String downloadUrl;
    @Nullable
    private String expireAt;

    public long getExportId() {
        return exportId;
    }

    public long getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Nullable
    public String getExpireAt() {
        return expireAt;
    }
}
