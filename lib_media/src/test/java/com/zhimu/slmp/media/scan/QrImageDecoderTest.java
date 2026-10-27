package com.zhimu.slmp.media.scan;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class QrImageDecoderTest {
    private static final String CONTENT = "http://127.0.0.1:5173/s/b/k7Rm2xQ9pT";

    private int[] qr(String text, boolean transparent, boolean inverted) throws Exception {
        BitMatrix bits = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 384, 384,
                Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        int[] pixels = new int[384 * 384];
        for (int y = 0; y < 384; y++) {
            for (int x = 0; x < 384; x++) {
                boolean dark = bits.get(x, y) ^ inverted;
                pixels[y * 384 + x] = dark ? 0xFF000000 : transparent ? 0x00000000 : 0xFFFFFFFF;
            }
        }
        return pixels;
    }

    @Test public void decodesTheBatchPayloadWithoutChangingItsProtocol() throws Exception {
        assertEquals(CONTENT, QrImageDecoder.decodeArgb(qr(CONTENT, false, false), 384, 384, 1));
    }

    @Test public void handlesAllExifRotationsAndMirrors() throws Exception {
        int[] pixels = qr(CONTENT, false, false);
        for (int orientation = 1; orientation <= 8; orientation++) {
            assertEquals("EXIF orientation " + orientation, CONTENT,
                    QrImageDecoder.decodeArgb(pixels, 384, 384, orientation));
        }
    }

    @Test public void handlesTransparentMarginsAndInvertedQrImages() throws Exception {
        assertEquals(CONTENT, QrImageDecoder.decodeArgb(qr(CONTENT, true, false), 384, 384, 1));
        assertEquals(CONTENT, QrImageDecoder.decodeArgb(qr(CONTENT, false, true), 384, 384, 1));
    }

    @Test public void preservesUtf8AndLeavesBusinessValidationToTheCaller() throws Exception {
        String other = "这不是批次二维码 https://example.com/other";
        assertEquals(other, QrImageDecoder.decodeArgb(qr(other, false, false), 384, 384, 1));
    }

    @Test public void blankImagesDoNotProduceFakeResults() {
        int[] pixels = new int[384 * 384];
        Arrays.fill(pixels, 0xFFFFFFFF);
        assertNull(QrImageDecoder.decodeArgb(pixels, 384, 384, 1));
    }

    @Test public void samplesLargeInputsWithinTheMemoryBudgetIncludingIntegerBounds() {
        for (int[] size : new int[][]{{8193, 6000}, {8000, 6000}, {4000, 3000}, {2048, 2048},
                {Integer.MAX_VALUE, Integer.MAX_VALUE}, {1, Integer.MAX_VALUE}}) {
            int sample = QrImageDecoder.sampleSize(size[0], size[1]);
            long width = ((long) size[0] + sample - 1) / sample;
            long height = ((long) size[1] + sample - 1) / sample;
            assertTrue(width <= QrImageDecoder.MAX_SIDE && height <= QrImageDecoder.MAX_SIDE);
            assertTrue(width * height <= QrImageDecoder.MAX_PIXELS);
        }
        assertEquals(1, QrImageDecoder.sampleSize(768, 768));
    }

    @Test public void rotatesNonSquareLuminanceWithoutChangingPixelCount() {
        int[] pixels = {0xFF010101, 0xFF020202, 0xFF030303, 0xFF040404, 0xFF050505, 0xFF060606};
        com.google.zxing.LuminanceSource rotated = QrImageDecoder.luminance(pixels, 2, 3, 6);
        assertEquals(3, rotated.getWidth());
        assertEquals(2, rotated.getHeight());
        assertArrayEquals(new byte[]{5, 3, 1, 6, 4, 2}, rotated.getMatrix());
    }
}
