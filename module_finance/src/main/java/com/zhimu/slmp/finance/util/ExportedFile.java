package com.zhimu.slmp.finance.util;

import androidx.annotation.NonNull;

/**
 * 已下载到专属外部目录（Download/reports/）的导出文件：文件名、绝对路径与 MIME。
 */
public final class ExportedFile {

    @NonNull
    private final String fileName;
    @NonNull
    private final String path;
    @NonNull
    private final String mimeType;

    public ExportedFile(@NonNull String fileName, @NonNull String path, @NonNull String mimeType) {
        this.fileName = fileName;
        this.path = path;
        this.mimeType = mimeType;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }
}
