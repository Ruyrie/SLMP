package com.zhimu.server.integration.media;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * 图片处理（04 号 8.2 节）：只支持 JPG、PNG；读取宽高，生成长边 360px 的 JPEG 缩略图（不放大，透明背景填白）。
 * 不支持或无法解码的图片抛出 40002。
 */
public class ImageProcessor {

    public static final int THUMB_LONG_SIDE = 360;
    /** 解码前检查像素数，防止超大尺寸图片耗尽内存 */
    private static final long MAX_PIXELS = 60_000_000L;
    private static final float JPEG_QUALITY = 0.85f;

    /** 只读文件头取宽高与格式，不解码像素 */
    public ImageInfo readInfo(byte[] data) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = reader(iis);
            try {
                reader.setInput(iis, true, true);
                return new ImageInfo(reader.getWidth(0), reader.getHeight(0), format(reader), mime(reader));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid();
        }
    }

    public byte[] thumbnail(byte[] data) {
        return thumbnail(data, THUMB_LONG_SIDE);
    }

    /** 长边缩到 longSide（原图更小时保持原尺寸），输出 JPEG */
    public byte[] thumbnail(byte[] data, int longSide) {
        BufferedImage src = decode(data);
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, longSide / (double) Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        BufferedImage current = src;
        // 逐级减半再缩放，避免一次大比例缩小产生锯齿
        while (current.getWidth() / 2 >= tw && current.getHeight() / 2 >= th) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        BufferedImage out = draw(current, tw, th);
        return encodeJpeg(out);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static BufferedImage decode(byte[] data) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = reader(iis);
            try {
                reader.setInput(iis, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw invalid();
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BizException be) {
                throw be;
            }
            throw invalid();
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new MediaException("缩略图生成失败", e.getMessage());
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static ImageReader reader(ImageInputStream iis) {
        if (iis == null) {
            throw invalid();
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw invalid();
        }
        ImageReader reader = readers.next();
        if (format(reader) == null) {
            reader.dispose();
            throw invalid();
        }
        return reader;
    }

    private static String format(ImageReader reader) {
        try {
            String name = reader.getFormatName().toLowerCase(Locale.ROOT);
            return switch (name) {
                case "jpeg", "jpg" -> "JPEG";
                case "png" -> "PNG";
                default -> null;
            };
        } catch (IOException e) {
            return null;
        }
    }

    private static String mime(ImageReader reader) {
        return "PNG".equals(format(reader)) ? "image/png" : "image/jpeg";
    }

    private static BizException invalid() {
        return new BizException(ErrorCode.FILE_INVALID);
    }
}
