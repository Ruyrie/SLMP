package com.zhimu.slmp.herd.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.zhimu.slmp.media.util.GallerySaver;

import java.io.File;
import java.io.FileOutputStream;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 批次标签图片：白底，上方二维码，下方批次号（大字、等宽）与 label_text（“东山猪场 · 4 号育肥舍 · 杜长大”）。
 * 保存时先写到 cacheDir/label 再经 lib_media 的 GallerySaver 写入系统相册（MediaStore 先待写入后公开，不申请存储权限）。
 */
public final class QrLabelRenderer {

    /** 标签宽度（像素） */
    public static final int LABEL_WIDTH = 720;
    private static final int PADDING = 48;
    private static final int QR_SIZE = LABEL_WIDTH - PADDING * 2;

    private QrLabelRenderer() {
    }

    /** 生成标签位图（computation 线程） */
    @NonNull
    public static Single<Bitmap> renderAsync(@NonNull String qrContent, @NonNull String batchNo,
                                             @Nullable String labelText) {
        return Single.fromCallable(() -> render(qrContent, batchNo, labelText)).subscribeOn(Schedulers.computation());
    }

    @WorkerThread
    @NonNull
    public static Bitmap render(@NonNull String qrContent, @NonNull String batchNo, @Nullable String labelText)
            throws Exception {
        Bitmap qr = QrBitmapEncoder.encode(qrContent, QR_SIZE);
        Paint noPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        noPaint.setColor(Color.BLACK);
        noPaint.setTextSize(64f);
        noPaint.setTypeface(Typeface.MONOSPACE);
        noPaint.setTextAlign(Paint.Align.CENTER);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF444746);
        labelPaint.setTextSize(32f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        Rect bounds = new Rect();
        noPaint.getTextBounds(batchNo, 0, batchNo.length(), bounds);
        int noHeight = bounds.height();
        int labelHeight = 0;
        if (labelText != null && !labelText.isEmpty()) {
            labelPaint.getTextBounds(labelText, 0, labelText.length(), bounds);
            labelHeight = bounds.height() + 24;
        }
        int height = PADDING + QR_SIZE + 32 + noHeight + labelHeight + PADDING;
        Bitmap out = Bitmap.createBitmap(LABEL_WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(qr, PADDING, PADDING, null);
        float y = PADDING + QR_SIZE + 32 + noHeight;
        canvas.drawText(batchNo, LABEL_WIDTH / 2f, y, noPaint);
        if (labelHeight > 0) {
            canvas.drawText(labelText, LABEL_WIDTH / 2f, y + labelHeight, labelPaint);
        }
        qr.recycle();
        return out;
    }

    /** 把标签保存到系统相册（io 线程） */
    @NonNull
    public static Completable saveToGallery(@NonNull Context context, @NonNull Bitmap label, @NonNull String batchNo) {
        Context app = context.getApplicationContext();
        return Completable.defer(() -> {
            File dir = new File(app.getCacheDir(), "label");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new java.io.IOException("mkdirs failed");
            }
            File file = new File(dir, "label_" + batchNo + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                label.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return GallerySaver.saveImage(app, file.getAbsolutePath(), 0)
                    .doFinally(() -> {
                        // 临时文件写入相册后删除
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    });
        }).subscribeOn(Schedulers.io());
    }
}
