package com.zhimu.server.integration.media;

import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static byte[] image(int w, int h, String format, int type) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 30, 30, 128));
        g.fillRect(0, 0, w / 2, h);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        return bos.toByteArray();
    }

    @Test
    void png_infoAndThumbnail() throws IOException {
        byte[] png = image(800, 600, "png", BufferedImage.TYPE_INT_ARGB);
        assertThat(processor.readInfo(png)).isEqualTo(new ImageInfo(800, 600, "PNG", "image/png"));

        byte[] thumb = processor.thumbnail(png);
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(thumb));
        assertThat(t.getWidth()).isEqualTo(360);
        assertThat(t.getHeight()).isEqualTo(270);
        assertThat(processor.readInfo(thumb).format()).isEqualTo("JPEG");
        // 透明区域填白
        assertThat(new Color(t.getRGB(350, 10)).getRed()).isGreaterThan(240);
    }

    @Test
    void jpeg_portraitThumbnail_andSmallImageNotEnlarged() throws IOException {
        byte[] jpg = image(1080, 1920, "jpg", BufferedImage.TYPE_INT_RGB);
        assertThat(processor.readInfo(jpg)).isEqualTo(new ImageInfo(1080, 1920, "JPEG", "image/jpeg"));
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(processor.thumbnail(jpg)));
        assertThat(t.getWidth()).isEqualTo(203);
        assertThat(t.getHeight()).isEqualTo(360);

        byte[] small = image(200, 100, "jpg", BufferedImage.TYPE_INT_RGB);
        BufferedImage s = ImageIO.read(new ByteArrayInputStream(processor.thumbnail(small)));
        assertThat(s.getWidth()).isEqualTo(200);
        assertThat(s.getHeight()).isEqualTo(100);
    }

    @Test
    void unsupportedFormats_40002() throws IOException {
        byte[] gif = image(10, 10, "gif", BufferedImage.TYPE_INT_RGB);
        assertThatThrownBy(() -> processor.readInfo(gif))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40002));
        assertThatThrownBy(() -> processor.thumbnail(gif))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40002));
        assertThatThrownBy(() -> processor.thumbnail("hello".getBytes()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40002));
    }
}
