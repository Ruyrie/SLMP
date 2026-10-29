package com.zhimu.slmp.finance.util;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.vo.ReportExportVO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * 经营报表导出文件（技术准备文档 2.6.4、2.7）：系统 DownloadManager 下载到专属外部目录 Download/reports/，
 * 同名旧文件先删；FileProvider 打开与分享；CreateDocument 另存；启动时删 7 天前文件，退出登录时删整个目录。
 * download_url 为无需访问令牌的签名地址，不向系统下载服务传令牌。
 * <p>Mock 示例文件的地址以 {@link #ASSET_PREFIX} 开头时直接从 assets 复制（离线演示，不经系统下载）。
 */
@FeatureScope
public class ReportFileHelper {

    public static final String DIR = "reports";
    public static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_PDF = "application/pdf";
    public static final String ASSET_PREFIX = "file:///android_asset/";
    /** {@link #enqueue} 直接完成（assets 示例文件）时返回的下载 ID */
    public static final long LOCAL_DONE = Long.MIN_VALUE;
    static final long KEEP_DAYS = 7;

    /** 下载状态 */
    public static final class Status {
        public static final int RUNNING = 0;
        public static final int SUCCESSFUL = 1;
        public static final int FAILED = 2;

        public final int state;
        public final int reason;
        @Nullable
        public final ExportedFile file;
        /** 已下载字节数（RUNNING 时用于判断是否还有进展；系统下载在 PAUSED / PENDING 时不变） */
        public final long bytes;

        public Status(int state, int reason, @Nullable ExportedFile file) {
            this(state, reason, file, 0);
        }

        public Status(int state, int reason, @Nullable ExportedFile file, long bytes) {
            this.state = state;
            this.reason = reason;
            this.file = file;
            this.bytes = bytes;
        }
    }

    private final Context context;
    @Nullable
    private ExportedFile lastLocal;

    @Inject
    public ReportFileHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public static String mimeOf(@NonNull String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ? MIME_PDF : MIME_XLSX;
    }

    @Nullable
    File dir() {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return base == null ? null : new File(base, DIR);
    }

    /** 删同名旧文件后加入系统下载；返回下载 ID（assets 示例文件返回 {@link #LOCAL_DONE}） */
    @WorkerThread
    public long enqueue(@NonNull ReportExportVO vo) throws IOException {
        File dir = dir();
        if (dir == null) {
            throw new IOException("外部存储不可用");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录");
        }
        String name = safeName(vo.getFileName());
        File target = new File(dir, name);
        if (target.exists() && !target.delete()) {
            throw new IOException("无法覆盖同名文件");
        }
        String url = vo.getDownloadUrl();
        if (url.startsWith(ASSET_PREFIX)) {
            try (InputStream in = context.getAssets().open(url.substring(ASSET_PREFIX.length()));
                 OutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
            lastLocal = new ExportedFile(name, target.getAbsolutePath(), mimeOf(name));
            return LOCAL_DONE;
        }
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, DIR + "/" + name)
                .setMimeType(mimeOf(name))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        return dm.enqueue(req);
    }

    /** 按下载 ID 查询状态（DownloadManager.Query 的 COLUMN_STATUS） */
    @WorkerThread
    @NonNull
    public Status query(long downloadId) {
        if (downloadId == LOCAL_DONE) {
            return lastLocal == null ? new Status(Status.FAILED, 0, null)
                    : new Status(Status.SUCCESSFUL, 0, lastLocal);
        }
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (c == null || !c.moveToFirst()) {
                return new Status(Status.FAILED, 0, null);
            }
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                String title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                File f = local == null ? null : new File(Uri.parse(local).getPath());
                if (f == null || !f.isFile()) {
                    return new Status(Status.FAILED, 0, null);
                }
                String name = title == null ? f.getName() : title;
                return new Status(Status.SUCCESSFUL, 0, new ExportedFile(name, f.getAbsolutePath(), mimeOf(name)));
            }
            if (status == DownloadManager.STATUS_FAILED) {
                return new Status(Status.FAILED, c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                        null);
            }
            long bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            return new Status(Status.RUNNING, 0, null, Math.max(bytes, 0));
        }
    }

    /** 长时间没有进展时取消系统下载（同时删除未完成的文件） */
    @WorkerThread
    public void cancel(long downloadId) {
        if (downloadId == LOCAL_DONE) {
            return;
        }
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.remove(downloadId);
    }

    /** CreateDocument 另存：复制到用户选择的位置 */
    @WorkerThread
    public void copyTo(@NonNull ExportedFile file, @NonNull Uri target) throws IOException {
        try (InputStream in = new FileInputStream(file.getPath());
             OutputStream out = context.getContentResolver().openOutputStream(target)) {
            if (out == null) {
                throw new IOException("无法写入所选位置");
            }
            copy(in, out);
        }
    }

    /** FileProvider content Uri（打开、分享） */
    @NonNull
    public Uri contentUri(@NonNull ExportedFile file) {
        return FileProvider.getUriForFile(context, authority(context), new File(file.getPath()));
    }

    @NonNull
    public static String authority(@NonNull Context context) {
        return context.getPackageName() + ".finance.fileprovider";
    }

    /** 启动时删除 7 天前的导出文件 */
    @WorkerThread
    public void cleanupOld() {
        File dir = dir();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        long expire = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS);
        for (File f : files) {
            if (f.lastModified() < expire) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** 退出登录时删除整个目录（clearLocalData） */
    @WorkerThread
    public void clearAll() {
        File dir = dir();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @NonNull
    static String safeName(@NonNull String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "经营报表" : s;
    }

    private static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }
}
