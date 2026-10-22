package com.zhimu.slmp.media.compress;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.zhimu.slmp.media.util.MediaFiles;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import javax.inject.Inject;

/**
 * 图片压缩（03 号规范 8.3 节）：采样解码 → 按 EXIF 方向纠正 → 长边不超过 1920 像素 → JPEG 质量 85 →
 * 超过 5MB 时每次降低 10 直到 40，输出到 cacheDir/compress。只接受 JPG、PNG（PNG 的透明区域填白色）。
 * <p>
 * 所有方法都是阻塞的，只能在 io 线程调用。
 */
public class ImageCompressor {

    public static final int MAX_LONG_SIDE = 1920;
    public static final int QUALITY = 85;
    public static final int MIN_QUALITY = 40;
    public static final int QUALITY_STEP = 10;
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";

    private final Context context;

    @Inject
    public ImageCompressor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    // ===== 校验 =====

    /** 读取图片类型：content:// 先取 ContentResolver 的类型，取不到或为文件时按文件头解码判断；无法读取返回 null */
    @WorkerThread
    @Nullable
    public String detectMime(@NonNull Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String type = context.getContentResolver().getType(uri);
            if (type != null && type.startsWith("image/")) {
                return type.toLowerCase(Locale.ROOT);
            }
        }
        BitmapFactory.Options bounds = readBounds(uri);
        return bounds == null ? null : bounds.outMimeType;
    }

    /** 是否为允许上传的图片类型（JPG、PNG） */
    public static boolean isSupportedMime(@Nullable String mime) {
        if (mime == null) {
            return false;
        }
        String m = mime.toLowerCase(Locale.ROOT);
        return MIME_JPEG.equals(m) || "image/jpg".equals(m) || MIME_PNG.equals(m);
    }

    // ===== 压缩 =====

    /**
     * 压缩一张图片。
     *
     * @return cacheDir/compress 下的 JPEG 文件
     * @throws MediaFileException 类型不支持、无法读取或压缩后仍超过 5MB（message 可直接显示）
     */
    @WorkerThread
    @NonNull
    public File compress(@NonNull Uri source) throws IOException, MediaFileException {
        BitmapFactory.Options bounds = readBounds(source);
        if (bounds == null || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new MediaFileException(MediaFileException.MSG_UNREADABLE);
        }
        if (!isSupportedMime(bounds.outMimeType)) {
            throw new MediaFileException(MediaFileException.MSG_IMAGE_INVALID);
        }
        int orientation = MIME_PNG.equals(bounds.outMimeType)
                ? ExifInterface.ORIENTATION_NORMAL : readOrientation(source);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_LONG_SIDE);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream in = open(source)) {
            decoded = BitmapFactory.decodeStream(in, null, opts);
        } catch (OutOfMemoryError e) {
            throw new MediaFileException(MediaFileException.MSG_UNREADABLE);
        }
        if (decoded == null) {
            throw new MediaFileException(MediaFileException.MSG_UNREADABLE);
        }

        Bitmap output = transform(decoded, orientation);
        byte[] jpeg = encode(output);
        if (output != decoded) {
            output.recycle();
        }
        decoded.recycle();
        if (jpeg == null) {
            throw new MediaFileException(MediaFileException.MSG_IMAGE_INVALID);
        }
        File file = MediaFiles.newCompressFile(context);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(jpeg);
        }
        return file;
    }

    /** 缩放到长边 1920、按 EXIF 旋转或镜像；带透明通道时铺白底 */
    @NonNull
    private Bitmap transform(@NonNull Bitmap src, int orientation) {
        int[] target = scaledSize(src.getWidth(), src.getHeight(), MAX_LONG_SIDE);
        float scale = target[0] / (float) src.getWidth();
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postConcat(orientationMatrix(orientation));
        Bitmap out = m.isIdentity() ? src : Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out.hasAlpha()) {
            Bitmap opaque = Bitmap.createBitmap(out.getWidth(), out.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(out, 0, 0, null);
            if (out != src) {
                out.recycle();
            }
            out = opaque;
        }
        return out;
    }

    /** JPEG 编码：质量 85 起，超过 5MB 时逐步降低；降到 40 仍超出返回 null */
    @Nullable
    private static byte[] encode(@NonNull Bitmap bitmap) {
        int quality = QUALITY;
        ByteArrayOutputStream out = new ByteArrayOutputStream(512 * 1024);
        while (quality > 0) {
            out.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            if (out.size() <= MAX_BYTES) {
                return out.toByteArray();
            }
            quality = nextQuality(quality);
        }
        return null;
    }

    @NonNull
    private static Matrix orientationMatrix(int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setRotate(180);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(-90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(-90);
                break;
            default:
                break;
        }
        return m;
    }

    // ===== 纯计算（单元测试） =====

    /** 采样率：2 的幂，采样后长边仍不小于 maxLongSide（避免先缩小再放大） */
    @VisibleForTesting
    public static int calculateInSampleSize(int width, int height, int maxLongSide) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= maxLongSide) {
            sample *= 2;
        }
        return sample;
    }

    /** 等比缩放到长边不超过 maxLongSide 的尺寸 {宽, 高}；本来就不超过时原样返回 */
    @VisibleForTesting
    @NonNull
    public static int[] scaledSize(int width, int height, int maxLongSide) {
        int longSide = Math.max(width, height);
        if (longSide <= maxLongSide || longSide <= 0) {
            return new int[]{width, height};
        }
        double scale = maxLongSide / (double) longSide;
        int w = Math.max(1, (int) Math.round(width * scale));
        int h = Math.max(1, (int) Math.round(height * scale));
        return new int[]{w, h};
    }

    /** 下一档质量；低于最低质量时返回 0（停止） */
    @VisibleForTesting
    public static int nextQuality(int quality) {
        int next = quality - QUALITY_STEP;
        return next < MIN_QUALITY ? 0 : next;
    }

    /** EXIF 方向是否交换宽高（转 90 / 270 度的四种） */
    @VisibleForTesting
    public static boolean swapsDimensions(int orientation) {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_ROTATE_270
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    /** 纠正方向并压缩后的输出尺寸 {宽, 高} */
    @VisibleForTesting
    @NonNull
    public static int[] outputSize(int width, int height, int orientation) {
        int sample = calculateInSampleSize(width, height, MAX_LONG_SIDE);
        int[] size = scaledSize(width / sample, height / sample, MAX_LONG_SIDE);
        return swapsDimensions(orientation) ? new int[]{size[1], size[0]} : size;
    }

    // ===== 读取 =====

    @Nullable
    private BitmapFactory.Options readBounds(@NonNull Uri uri) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = open(uri)) {
            BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException | SecurityException e) {
            return null;
        }
        return opts.outWidth > 0 ? opts : null;
    }

    private int readOrientation(@NonNull Uri uri) {
        try (InputStream in = open(uri)) {
            return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | SecurityException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    @NonNull
    private InputStream open(@NonNull Uri uri) throws IOException {
        InputStream in;
        try {
            in = context.getContentResolver().openInputStream(uri);
        } catch (SecurityException e) {
            throw new IOException(e);
        }
        if (in == null) {
            throw new IOException("无法打开 " + uri);
        }
        return in;
    }
}
