package com.zhimu.slmp.common.session;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.common.service.IAccountCleanupService;
import com.zhimu.slmp.common.service.ServiceHub;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

/** 只清理本应用拥有的个人临时文件；不访问系统相册、共享Downloads或测试证据目录。 */
@Singleton
public class AccountDataCleaner {
    private static final String TAG = "AccountCleanup";
    private static final String[] PERSONAL_CACHE_DIRS = {"capture", "compress", "pick", "upload", "voice", "label"};
    private final Context context;
    private final AppSettings settings;

    @Inject public AccountDataCleaner(@NonNull Context context, @NonNull AppSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    @WorkerThread public void stopPendingWork() {
        IAccountCleanupService service = ServiceHub.get(IAccountCleanupService.class);
        if (service != null) {
            try {
                if (!service.stopPendingWork().blockingAwait(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "停止账号后台任务超时");
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "停止账号后台任务失败", e);
            }
        }
    }

    @WorkerThread public void clear() {
        try { settings.clearAccountData(); }
        catch (RuntimeException e) { Log.w(TAG, "清理账号偏好失败", e); }
        try {
            Glide.get(context).clearDiskCache();
            new Handler(Looper.getMainLooper()).post(() -> Glide.get(context).clearMemory());
        } catch (RuntimeException e) {
            Log.w(TAG, "清理图片缓存失败", e);
        }
        clearPrivateFiles(context.getCacheDir(), context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS));
    }

    static void clearPrivateFiles(File cache, File reports) {
        if (cache != null) {
            for (String name : PERSONAL_CACHE_DIRS) deleteWithin(cache, new File(cache, name));
            File[] avatars = cache.listFiles((dir, name) -> name.startsWith("avatar_"));
            if (avatars != null) for (File file : avatars) deleteWithin(cache, file);
        }
        if (reports != null) {
            File[] files = reports.listFiles();
            if (files != null) for (File file : files) deleteWithin(reports, file);
        }
    }

    private static void deleteWithin(File root, File file) {
        try {
            // A symlink must never escape the app-owned directory.
            String boundary = root.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(boundary)) return;
            File[] children = file.isDirectory() ? file.listFiles() : null;
            if (children != null) for (File child : children) deleteWithin(root, child);
            if (file.exists() && !file.delete()) Log.w(TAG, "个人临时文件清理未完成");
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "个人临时文件清理失败", e);
        }
    }
}
