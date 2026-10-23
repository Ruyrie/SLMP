package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.vo.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceImplTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private CaptchaServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new CaptchaServiceImpl(redis);
    }

    @Test
    void 生成4位验证码_写入Redis2分钟_返回112x40的PNG_data_url() throws Exception {
        CaptchaVO vo = service.create();

        assertThat(vo.getCaptchaId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(vo.getExpireSeconds()).isEqualTo(120);
        assertThat(vo.getImage()).startsWith("data:image/png;base64,");

        byte[] png = Base64.getDecoder().decode(vo.getImage().substring("data:image/png;base64,".length()));
        assertThat(png).isNotEmpty();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(112);
        assertThat(image.getHeight()).isEqualTo(40);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(CaptchaServiceImpl.KEY_PREFIX + vo.getCaptchaId()), codeCaptor.capture(),
                eq(Duration.ofSeconds(120)));
        assertThat(codeCaptor.getValue()).hasSize(4).matches("[2-9A-HJ-KM-NP-Z]{4}");
        assertThat(codeCaptor.getValue()).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void 校验不区分大小写且一次有效() {
        when(values.get("admin:captcha:abc")).thenReturn("A2B3");

        assertThat(service.verify("abc", "a2b3")).isTrue();
        verify(redis).delete("admin:captcha:abc");

        when(values.get("admin:captcha:abc")).thenReturn(null);
        assertThat(service.verify("abc", "a2b3")).isFalse();
    }

    @Test
    void 验证码错误也删除_不能重试() {
        when(values.get("admin:captcha:abc")).thenReturn("A2B3");
        assertThat(service.verify("abc", "ZZZZ")).isFalse();
        verify(redis).delete("admin:captcha:abc");
    }

    @Test
    void 缺少参数不查Redis直接失败() {
        assertThat(service.verify(null, "A2B3")).isFalse();
        assertThat(service.verify("abc", " ")).isFalse();
        verify(values, never()).get(any());
        verify(redis, never()).delete(any(String.class));
    }
}
