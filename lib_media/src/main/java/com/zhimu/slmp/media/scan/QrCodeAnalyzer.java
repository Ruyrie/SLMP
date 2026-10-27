package com.zhimu.slmp.media.scan;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraX ImageAnalysis 的二维码分析器（03 号规范 8.7 节、模块04 文档 2.6.3）：
 * 取 YUV_420_888 的 Y 平面亮度（以 rowStride 为数据宽度），优先识别中央正方形，失败后回退到完整有效画面。
 * 预览经过旋转和裁剪，界面上方的取景框不一定对应传感器中心，不能仅分析固定中心区域。
 * ZXing QRCodeReader 只识别 QR_CODE、UTF-8；未识别继续下一帧，识别成功后自动停止并在主线程回调。
 * finally 中关闭 ImageProxy，避免分析停住。
 * <p>
 * 配合 {@code ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST} 与单线程执行器使用；相机页 I08 的扫码模式即用本类，
 * 自行实现扫码页面（如 L07）时也可以直接使用。继续识别调用 {@link #setEnabled(boolean)}。
 */
public class QrCodeAnalyzer implements ImageAnalysis.Analyzer {

    /** 识别结果回调（主线程） */
    public interface Listener {
        void onQrDecoded(@NonNull String text);
    }

    /** 中央裁剪区域占短边的比例 */
    private static final float CROP_RATIO = 0.8f;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final QRCodeReader reader = new QRCodeReader();
    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    @Nullable
    private byte[] buffer;

    public QrCodeAnalyzer(@NonNull Listener listener) {
        this.listener = listener;
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    /** 开始或暂停识别（识别成功后自动暂停） */
    public void setEnabled(boolean on) {
        enabled.set(on);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!enabled.get()) {
                return;
            }
            String text = decode(image);
            if (text != null && enabled.compareAndSet(true, false)) {
                main.post(() -> listener.onQrDecoded(text));
            }
        } finally {
            image.close();
        }
    }

    @Nullable
    @VisibleForTesting
    String decode(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer y = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int size = rowStride * height;
        if (buffer == null || buffer.length < size) {
            buffer = new byte[size];
        }
        y.rewind();
        y.get(buffer, 0, Math.min(y.remaining(), size));

        int[] crop = centerCrop(width, height, CROP_RATIO);
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(buffer, rowStride, height,
                crop[0], crop[1], crop[2], crop[3], false);
        String text = read(source);
        if (text != null) return text;
        // Keep row padding out of the valid image while allowing visible off-center labels.
        return read(new PlanarYUVLuminanceSource(buffer, rowStride, height, 0, 0, width, height, false));
    }

    @Nullable
    private String read(@NonNull PlanarYUVLuminanceSource source) {
        try {
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
            return result.getText();
        } catch (ReaderException e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    /** 画面中央正方形 {left, top, width, height}，边长为短边乘以 ratio */
    @VisibleForTesting
    @NonNull
    public static int[] centerCrop(int width, int height, float ratio) {
        int side = Math.max(1, (int) (Math.min(width, height) * ratio));
        int left = (width - side) / 2;
        int top = (height - side) / 2;
        return new int[]{left, top, side, side};
    }
}
