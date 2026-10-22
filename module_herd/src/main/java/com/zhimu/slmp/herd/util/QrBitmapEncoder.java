package com.zhimu.slmp.herd.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 批次二维码位图：ZXing QRCodeWriter（纠错 M，UTF-8，留 1 个模块的白边），在 computation 线程生成。
 */
public final class QrBitmapEncoder {

    private QrBitmapEncoder() {
    }

    /** computation 线程生成，调用方切回主线程 */
    @NonNull
    public static Single<Bitmap> encodeAsync(@NonNull String content, int sizePx) {
        return Single.fromCallable(() -> encode(content, sizePx)).subscribeOn(Schedulers.computation());
    }

    @WorkerThread
    @NonNull
    public static Bitmap encode(@NonNull String content, int sizePx) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(content, com.google.zxing.BarcodeFormat.QR_CODE,
                sizePx, sizePx, hints);
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
