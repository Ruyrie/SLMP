package com.zhimu.slmp.account.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 把恢复码图片写入系统相册 Pictures/智牧通（2.6.5、2.7 节）：MediaStore 以 IS_PENDING 方式写入后置为完成，
 * minSdk 31 无需存储权限；返回 Uri 供“查看”。在 io 线程调用。
 */
public final class RecoveryCodeImageSaver {

    public static final String ALBUM = "智牧通";

    private RecoveryCodeImageSaver() {
    }

    @WorkerThread
    @NonNull
    public static Uri save(@NonNull Context context, @NonNull Bitmap bitmap) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "智牧通恢复码_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("无法创建相册文件");
        }
        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                throw new IOException("写入图片失败");
            }
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }
}
