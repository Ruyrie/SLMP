package com.zhimu.slmp.media.scan;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class QrCodeAnalyzerTest {

    @Test
    public void centerCrop_squareInTheMiddle() {
        // 1280×720 帧：边长 720 × 0.8 = 576，居中
        assertArrayEquals(new int[]{352, 72, 576, 576}, QrCodeAnalyzer.centerCrop(1280, 720, 0.8f));
        // 竖向帧
        assertArrayEquals(new int[]{72, 352, 576, 576}, QrCodeAnalyzer.centerCrop(720, 1280, 0.8f));
        // 全幅
        assertArrayEquals(new int[]{0, 0, 640, 640}, QrCodeAnalyzer.centerCrop(640, 640, 1f));
    }
}
