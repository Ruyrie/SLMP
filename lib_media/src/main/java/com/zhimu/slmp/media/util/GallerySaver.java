package com.zhimu.slmp.media.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.reactivex.rxjava3.core.Completable;

/**
 * 保存图片到系统相册（03 号规范 8.1 节、模块07 文档 2.7.4）：通过 MediaStore 写入 Pictures/智牧通，
 * 先标记 IS_PENDING = 1，写完再置 0 公开；不申请存储权限（minSdk 31，写入本应用创建的媒体无需权限）。
 * 来源支持 http(s)（经 Glide 下载，按 file_id 缓存）、本地文件、content:// 与 file:///android_asset/。
 */
public final class GallerySaver {

    public static final String ALBUM = "智牧通";
    private static final String ASSET_PREFIX = "file:///android_asset/";

    private GallerySaver() {
    }

    /** 保存一张图片（在 io 线程订阅） */
    @NonNull
    public static Completable saveImage(@NonNull Context context, @NonNull String url, long fileId) {
        Context app = context.getApplicationContext();
        return Completable.fromAction(() -> saveImageBlocking(app, url, fileId));
    }

    @WorkerThread
    static void saveImageBlocking(@NonNull Context context, @NonNull String url, long fileId) throws Exception {
        File temp = null;
        InputStream in;
        if (MediaGlide.isRemoteUrl(url)) {
            Object model = MediaGlide.model(url, fileId);
            temp = Glide.with(context).asFile().load(model).submit().get();
            in = new FileInputStream(temp);
        } else {
            in = open(context, url);
        }
        String mime = detectMime(context, url, temp);
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        String ext = "image/png".equals(mime) ? ".png" : ".jpg";
        String name = new SimpleDateFormat("'ZMT_'yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date()) + ext;
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (target == null) {
            in.close();
            throw new IOException("MediaStore insert failed");
        }
        try (InputStream src = in; OutputStream out = resolver.openOutputStream(target)) {
            if (out == null) {
                throw new IOException("openOutputStream failed");
            }
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = src.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            resolver.delete(target, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(target, values, null, null);
    }

    @NonNull
    private static InputStream open(@NonNull Context context, @NonNull String url) throws IOException {
        if (url.startsWith(ASSET_PREFIX)) {
            return context.getAssets().open(url.substring(ASSET_PREFIX.length()));
        }
        String path = MediaFiles.toPath(url);
        if (path != null) {
            return new FileInputStream(path);
        }
        InputStream in = context.getContentResolver().openInputStream(Uri.parse(url));
        if (in == null) {
            throw new IOException("无法打开 " + url);
        }
        return in;
    }

    @NonNull
    private static String detectMime(@NonNull Context context, @NonNull String url, @Nullable File downloaded) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = downloaded != null ? new FileInputStream(downloaded) : open(context, url)) {
            BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            return "image/jpeg";
        }
        return "image/png".equals(opts.outMimeType) ? "image/png" : "image/jpeg";
    }
}
