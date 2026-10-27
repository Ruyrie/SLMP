package com.zhimu.slmp;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.zhimu.slmp.herd.util.QrBitmapEncoder;
import com.zhimu.slmp.media.scan.QrCodeAnalyzer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real label encoder and ZXing, using CameraX's padded Y-plane contract; no mocked decoded text. */
@RunWith(AndroidJUnit4.class)
public class BatchQrImageAnalysisTest {
    private static final String QR = "https://web.zhimutong.example.com/s/b/k7Rm2xQ9pT";

    @Test public void batchLabelDecodesFromPaddedLandscapeFrame() throws Exception {
        assertDecodes(false);
    }

    @Test public void rotatedBatchLabelDecodesFromCameraFrame() throws Exception {
        assertDecodes(true);
    }

    @Test public void labelAboveTheCenterRegionDecodesFromARealBitmap() throws Exception {
        assertDecodes(false, 550, 4, 180);
    }

    @Test public void rotatedLabelBesideTheCenterRegionDecodesFromARealBitmap() throws Exception {
        assertDecodes(true, 24, 250, 200);
    }

    private void assertDecodes(boolean rotated) throws Exception {
        assertDecodes(rotated, (Frame.WIDTH - 512) / 2, (Frame.HEIGHT - 512) / 2, 512);
    }

    private void assertDecodes(boolean rotated, int left, int top, int size) throws Exception {
        Bitmap qr = QrBitmapEncoder.encode(QR, size);
        if (rotated) {
            Matrix rotation = new Matrix();
            rotation.postRotate(90);
            Bitmap original = qr;
            qr = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), rotation, false);
            original.recycle();
        }
        Frame frame = new Frame(qr, left, top);
        qr.recycle();
        CountDownLatch decoded = new CountDownLatch(1);
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        QrCodeAnalyzer analyzer = new QrCodeAnalyzer(text -> {
            actual.set(text);
            callbacks.incrementAndGet();
            decoded.countDown();
        });
        analyzer.analyze(frame);
        assertTrue("A real QR image should decode", decoded.await(5, TimeUnit.SECONDS));
        assertEquals(QR, actual.get());
        assertTrue("CameraX frame must always be released", frame.closed);
        assertFalse("Successful analysis must pause duplicate frames", analyzer.isEnabled());
        Frame repeated = new Frame(null);
        analyzer.analyze(repeated);
        assertTrue(repeated.closed);
        assertEquals(1, callbacks.get());
    }

    @Test public void blankAndDisabledFramesAreReleasedWithoutAResult() {
        AtomicInteger callbacks = new AtomicInteger();
        QrCodeAnalyzer analyzer = new QrCodeAnalyzer(text -> callbacks.incrementAndGet());
        Frame blank = new Frame(null);
        analyzer.analyze(blank);
        assertTrue(blank.closed);
        assertTrue("No QR should leave scanning active", analyzer.isEnabled());
        analyzer.setEnabled(false);
        Frame paused = new Frame(null);
        analyzer.analyze(paused);
        assertTrue(paused.closed);
        assertEquals(0, callbacks.get());
    }

    private static final class Frame implements ImageProxy {
        private static final int WIDTH = 1280;
        private static final int HEIGHT = 720;
        private static final int STRIDE = 1344;
        private final ByteBuffer y;
        private Rect crop = new Rect(0, 0, WIDTH, HEIGHT);
        private boolean closed;

        Frame(@Nullable Bitmap qr) {
            this(qr, qr == null ? 0 : (WIDTH - qr.getWidth()) / 2,
                    qr == null ? 0 : (HEIGHT - qr.getHeight()) / 2);
        }

        Frame(@Nullable Bitmap qr, int left, int top) {
            // The last row need not include padding; real camera planes may be this short.
            byte[] pixels = new byte[STRIDE * (HEIGHT - 1) + WIDTH];
            Arrays.fill(pixels, (byte) 255);
            if (qr != null) {
                for (int row = 0; row < qr.getHeight(); row++) {
                    for (int column = 0; column < qr.getWidth(); column++) {
                        pixels[(top + row) * STRIDE + left + column] = (byte) (qr.getPixel(column, row) & 255);
                    }
                }
            }
            y = ByteBuffer.wrap(pixels);
        }

        @Override public void close() { closed = true; }
        @Override public int getFormat() { return ImageFormat.YUV_420_888; }
        @Override public int getHeight() { return HEIGHT; }
        @Override public int getWidth() { return WIDTH; }
        @NonNull @Override public Rect getCropRect() { return crop; }
        @Override public void setCropRect(@Nullable Rect rect) { crop = rect; }
        @NonNull @Override public ImageInfo getImageInfo() { throw new UnsupportedOperationException(); }
        @Nullable @Override public Image getImage() { return null; }
        @NonNull @Override public PlaneProxy[] getPlanes() {
            return new PlaneProxy[]{new PlaneProxy() {
                @Override public int getRowStride() { return STRIDE; }
                @Override public int getPixelStride() { return 1; }
                @NonNull @Override public ByteBuffer getBuffer() { return y; }
            }};
        }
    }
}
