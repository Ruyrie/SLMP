package com.zhimu.server.demo;

import com.zhimu.server.integration.storage.MinioObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SampleQualificationImagesTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MinioObjectStorage storage = mock(MinioObjectStorage.class);
    private final SampleQualificationImages initializer = new SampleQualificationImages(jdbc, storage);

    @Test
    void 上传可解码图片并补齐元数据防止已关联图片被清理() throws Exception {
        when(jdbc.queryForList(anyString())).thenReturn(rows());
        when(storage.ensureBuckets()).thenReturn(true);
        doAnswer(call -> {
            byte[] data = call.getArgument(2, InputStream.class).readAllBytes();
            var image = ImageIO.read(new java.io.ByteArrayInputStream(data));
            assertThat(image.getWidth()).isEqualTo(1200);
            assertThat(image.getHeight()).isEqualTo(840);
            assertThat(call.getArgument(3, Long.class)).isEqualTo((long) data.length);
            assertThat(call.getArgument(4, String.class)).isEqualTo("image/png");
            return null;
        }).when(storage).put(anyString(), anyString(), any(), anyLong(), anyString());

        initializer.run(null);

        verify(storage, times(4)).put(eq("zmt-private"), startsWith("demo/farm-qualifications/"), any(), anyLong(), eq("image/png"));
        verify(jdbc, times(4)).update(contains("upload_status='READY',bound=1"), anyInt(), matches("[a-f0-9]{64}"), any());
    }

    @Test
    void 重复启动不覆盖已经存在的对象() {
        when(jdbc.queryForList(anyString())).thenReturn(rows());
        when(storage.ensureBuckets()).thenReturn(true);
        when(storage.exists(anyString(), anyString())).thenReturn(true);
        initializer.run(null);
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString());
        verify(jdbc, times(4)).update(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void 存储暂不可用不伪报上传成功也不阻断应用启动() {
        when(jdbc.queryForList(anyString())).thenReturn(rows());
        when(storage.ensureBuckets()).thenReturn(true);
        when(storage.exists(anyString(), anyString())).thenThrow(new IllegalStateException("offline"));
        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(jdbc, never()).update(anyString(), anyInt(), anyString(), any());
    }

    private List<Map<String, Object>> rows() {
        return List.of("permit-before.png", "registration.png", "layout.png", "permit-after.png").stream()
                .map(name -> Map.<String, Object>of("id", 240101L, "bucket", "zmt-private", "object_key", "demo/farm-qualifications/" + name))
                .toList();
    }
}
