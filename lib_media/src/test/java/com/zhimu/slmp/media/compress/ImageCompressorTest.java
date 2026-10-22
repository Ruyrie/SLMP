package com.zhimu.slmp.media.compress;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.exifinterface.media.ExifInterface;

import org.junit.Test;

public class ImageCompressorTest {

    private static final int MAX = ImageCompressor.MAX_LONG_SIDE;

    @Test
    public void inSampleSize_keepsLongSideAtLeastTarget() {
        assertEquals(1, ImageCompressor.calculateInSampleSize(1000, 800, MAX));
        assertEquals(1, ImageCompressor.calculateInSampleSize(1920, 1080, MAX));
        assertEquals(1, ImageCompressor.calculateInSampleSize(3839, 2160, MAX));
        assertEquals(2, ImageCompressor.calculateInSampleSize(3840, 2160, MAX));
        assertEquals(2, ImageCompressor.calculateInSampleSize(4000, 3000, MAX));
        assertEquals(2, ImageCompressor.calculateInSampleSize(3000, 4000, MAX));
        assertEquals(4, ImageCompressor.calculateInSampleSize(8000, 6000, MAX));
        assertEquals(8, ImageCompressor.calculateInSampleSize(16000, 12000, MAX));
    }

    @Test
    public void scaledSize_longSideAtMost1920_keepsRatio() {
        assertArrayEquals(new int[]{1920, 1440}, ImageCompressor.scaledSize(4000, 3000, MAX));
        assertArrayEquals(new int[]{1440, 1920}, ImageCompressor.scaledSize(3000, 4000, MAX));
        assertArrayEquals(new int[]{1920, 1080}, ImageCompressor.scaledSize(2400, 1350, MAX));
        assertArrayEquals(new int[]{1080, 1920}, ImageCompressor.scaledSize(1080, 1920, MAX));
        assertArrayEquals(new int[]{640, 480}, ImageCompressor.scaledSize(640, 480, MAX));
        assertArrayEquals(new int[]{1920, 1}, ImageCompressor.scaledSize(9000, 2, MAX));
    }

    @Test
    public void outputSize_appliesSampleScaleAndRotation() {
        assertArrayEquals(new int[]{1920, 1440},
                ImageCompressor.outputSize(4000, 3000, ExifInterface.ORIENTATION_NORMAL));
        assertArrayEquals(new int[]{1440, 1920},
                ImageCompressor.outputSize(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90));
        assertArrayEquals(new int[]{1440, 1920},
                ImageCompressor.outputSize(4000, 3000, ExifInterface.ORIENTATION_TRANSVERSE));
        assertArrayEquals(new int[]{1920, 1440},
                ImageCompressor.outputSize(4000, 3000, ExifInterface.ORIENTATION_ROTATE_180));
        assertArrayEquals(new int[]{480, 640},
                ImageCompressor.outputSize(640, 480, ExifInterface.ORIENTATION_ROTATE_270));
    }

    @Test
    public void swapsDimensions_only90And270() {
        assertTrue(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_90));
        assertTrue(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_270));
        assertTrue(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_TRANSPOSE));
        assertTrue(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_TRANSVERSE));
        assertFalse(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_NORMAL));
        assertFalse(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_180));
        assertFalse(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_FLIP_HORIZONTAL));
        assertFalse(ImageCompressor.swapsDimensions(ExifInterface.ORIENTATION_UNDEFINED));
    }

    @Test
    public void nextQuality_stepsDownTo40ThenStops() {
        assertEquals(75, ImageCompressor.nextQuality(ImageCompressor.QUALITY));
        assertEquals(45, ImageCompressor.nextQuality(55));
        assertEquals(40, ImageCompressor.nextQuality(50));
        assertEquals(0, ImageCompressor.nextQuality(45));
        assertEquals(0, ImageCompressor.nextQuality(40));
        // 85 → 75 → 65 → 55 → 45 共 5 档
        int q = ImageCompressor.QUALITY;
        int steps = 0;
        while (q > 0) {
            steps++;
            q = ImageCompressor.nextQuality(q);
        }
        assertEquals(5, steps);
    }

    @Test
    public void supportedMime_onlyJpegAndPng() {
        assertTrue(ImageCompressor.isSupportedMime("image/jpeg"));
        assertTrue(ImageCompressor.isSupportedMime("IMAGE/JPEG"));
        assertTrue(ImageCompressor.isSupportedMime("image/jpg"));
        assertTrue(ImageCompressor.isSupportedMime("image/png"));
        assertFalse(ImageCompressor.isSupportedMime("image/heic"));
        assertFalse(ImageCompressor.isSupportedMime("image/webp"));
        assertFalse(ImageCompressor.isSupportedMime("image/gif"));
        assertFalse(ImageCompressor.isSupportedMime("video/mp4"));
        assertFalse(ImageCompressor.isSupportedMime(null));
    }

    @Test
    public void limits_matchSpec() {
        assertEquals(1920, ImageCompressor.MAX_LONG_SIDE);
        assertEquals(85, ImageCompressor.QUALITY);
        assertEquals(5L * 1024 * 1024, ImageCompressor.MAX_BYTES);
    }
}
