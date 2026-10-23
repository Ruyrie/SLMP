package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 图形验证码：4 位，字符集去掉 0、O、1、I、L；PNG 112×40 的 data URL；
 * Redis admin:captcha:{id} 有效 2 分钟，校验时读取并删除，一次有效，不区分大小写。
 */
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    public static final String KEY_PREFIX = "admin:captcha:";
    public static final int EXPIRE_SECONDS = 120;
    public static final int WIDTH = 112;
    public static final int HEIGHT = 40;
    public static final int CODE_LENGTH = 4;
    /** 去掉 0、O、1、I、L 的字符集 */
    public static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private final StringRedisTemplate redis;

    @Override
    public CaptchaVO create() {
        String code = randomCode();
        String captchaId = randomId();
        redis.opsForValue().set(KEY_PREFIX + captchaId, code, Duration.ofSeconds(EXPIRE_SECONDS));
        return CaptchaVO.builder()
                .captchaId(captchaId)
                .image(toDataUrl(code))
                .expireSeconds(EXPIRE_SECONDS)
                .build();
    }

    @Override
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + captchaId;
        String expected = redis.opsForValue().get(key);
        // 一次有效：无论匹配与否都删除
        redis.delete(key);
        return expected != null && expected.equals(captchaCode.trim().toUpperCase(Locale.ROOT));
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String randomId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 绘制 112×40 的 PNG 并转为 data URL；服务端只用于校验，不关心可读性以外的样式 */
    static String toDataUrl(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(247, 249, 252));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(150, 210));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                        RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
            }
            // 噪点
            for (int i = 0; i < 60; i++) {
                g.setColor(randomColor(120, 220));
                g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
            }
            // 字符，随机倾斜与颜色
            int step = WIDTH / (CODE_LENGTH + 1);
            for (int i = 0; i < code.length(); i++) {
                AffineTransform original = g.getTransform();
                double angle = (RANDOM.nextDouble() - 0.5) * 0.5;
                int x = step * (i + 1) - 6;
                int y = 28 + RANDOM.nextInt(4);
                g.rotate(angle, x, y);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24 + RANDOM.nextInt(4)));
                g.setColor(randomColor(20, 120));
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.setTransform(original);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + BASE64.encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }

    private static Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + RANDOM.nextInt(range), min + RANDOM.nextInt(range), min + RANDOM.nextInt(range));
    }
}
