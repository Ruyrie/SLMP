package com.zhimu.slmp.media.scan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class QrCodeAnalyzerTest {
    private static final int WIDTH = 1280, HEIGHT = 720, STRIDE = 1344;
    private static final String QR = "http://127.0.0.1:5173/s/b/k7Rm2xQ9pT";

    @Test
    public void centerCrop_squareInTheMiddle() {
        // 1280×720 帧：边长 720 × 0.8 = 576，居中
        assertArrayEquals(new int[]{352, 72, 576, 576}, QrCodeAnalyzer.centerCrop(1280, 720, 0.8f));
        // 竖向帧
        assertArrayEquals(new int[]{72, 352, 576, 576}, QrCodeAnalyzer.centerCrop(720, 1280, 0.8f));
        // 全幅
        assertArrayEquals(new int[]{0, 0, 640, 640}, QrCodeAnalyzer.centerCrop(640, 640, 1f));
    }

    @Test public void actualQrAboveTheCenterRegionStillDecodes() throws Exception {
        byte[] pixels = blank();
        draw(pixels, QR, 550, 4, 180, false);
        assertEquals(QR, analyzer().decode(frame(pixels)));
    }

    @Test public void actualQrBesideTheCenterRegionStillDecodesWhenRotated() throws Exception {
        byte[] pixels = blank();
        draw(pixels, QR, 24, 250, 200, true);
        assertEquals(QR, analyzer().decode(frame(pixels)));
    }

    @Test public void centeredCodeHasPriorityOverAnotherCodeAtTheEdge() throws Exception {
        byte[] pixels = blank();
        draw(pixels, QR, 550, 260, 180, false);
        draw(pixels, "http://127.0.0.1:5173/s/b/aaaaaaa234", 24, 250, 180, false);
        assertEquals(QR, analyzer().decode(frame(pixels)));
    }

    @Test public void readerAndPaddingDoNotRetainAnEarlierResult() throws Exception {
        QrCodeAnalyzer analyzer = analyzer();
        byte[] pixels = blank();
        draw(pixels, QR, 550, 260, 180, false);
        assertEquals(QR, analyzer.decode(frame(pixels)));
        assertNull(analyzer.decode(frame(blank())));
    }

    @Test public void pausedAndUnrecognizedFramesAreAlwaysReleased() {
        QrCodeAnalyzer analyzer = analyzer();
        ImageProxy blank = frame(blank());
        analyzer.analyze(blank);
        verify(blank).close();
        analyzer.setEnabled(false);
        ImageProxy paused = frame(blank());
        analyzer.analyze(paused);
        verify(paused).close();
        verify(paused, never()).getPlanes();
        assertFalse(analyzer.isEnabled());
    }

    private static QrCodeAnalyzer analyzer() { return new QrCodeAnalyzer(text -> {}); }

    private static byte[] blank() {
        byte[] pixels = new byte[STRIDE * (HEIGHT - 1) + WIDTH];
        Arrays.fill(pixels, (byte) 255);
        return pixels;
    }

    private static void draw(byte[] pixels, String content, int left, int top, int size, boolean rotated) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.CHARACTER_SET, "UTF-8"));
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean dark = rotated ? matrix.get(y, size - x - 1) : matrix.get(x, y);
                pixels[(top + y) * STRIDE + left + x] = dark ? (byte) 0 : (byte) 255;
            }
        }
    }

    private static ImageProxy frame(byte[] pixels) {
        ImageProxy image = mock(ImageProxy.class);
        ImageProxy.PlaneProxy plane = mock(ImageProxy.PlaneProxy.class);
        when(image.getWidth()).thenReturn(WIDTH);
        when(image.getHeight()).thenReturn(HEIGHT);
        when(image.getPlanes()).thenReturn(new ImageProxy.PlaneProxy[]{plane});
        when(plane.getRowStride()).thenReturn(STRIDE);
        when(plane.getPixelStride()).thenReturn(1);
        when(plane.getBuffer()).thenReturn(ByteBuffer.wrap(pixels));
        return image;
    }
}
