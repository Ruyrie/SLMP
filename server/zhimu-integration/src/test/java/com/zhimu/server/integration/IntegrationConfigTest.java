package com.zhimu.server.integration;

import com.zhimu.server.integration.ai.AiConfig;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiProperties;
import com.zhimu.server.integration.amap.AmapClient;
import com.zhimu.server.integration.amap.AmapConfig;
import com.zhimu.server.integration.amap.AmapProperties;
import com.zhimu.server.integration.ipregion.IpRegionConfig;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import com.zhimu.server.integration.ipregion.Ip2regionProperties;
import com.zhimu.server.integration.media.FfmpegProperties;
import com.zhimu.server.integration.media.ImageProcessor;
import com.zhimu.server.integration.media.MediaConfig;
import com.zhimu.server.integration.media.MediaProcessor;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.integration.push.PushConfig;
import com.zhimu.server.integration.push.PushProperties;
import com.zhimu.server.integration.sms.SmsClient;
import com.zhimu.server.integration.sms.SmsConfig;
import com.zhimu.server.integration.sms.SmsMode;
import com.zhimu.server.integration.sms.SmsProperties;
import com.zhimu.server.integration.storage.HlsProperties;
import com.zhimu.server.integration.storage.MinioProperties;
import com.zhimu.server.integration.storage.ObjectStorage;
import com.zhimu.server.integration.storage.StorageConfig;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 配置：application.yml 中 zhimu.integration 的每个键都能绑定到属性类（有多余或拼错的键时失败）；
 * 未配置任何 Key 时所有 Bean 都能创建；转码线程池不影响 Spring Boot 的 applicationTaskExecutor。
 */
class IntegrationConfigTest {

    private static final Path APP_YML = Path.of("..", "zhimu-app", "src", "main", "resources", "application.yml");

    @Getter
    @Setter
    public static class Root {
        private AiProperties ai = new AiProperties();
        private SmsProperties sms = new SmsProperties();
        private PushProperties push = new PushProperties();
        private AmapProperties amap = new AmapProperties();
        private MinioProperties minio = new MinioProperties();
        private HlsProperties hls = new HlsProperties();
        private FfmpegProperties ffmpeg = new FfmpegProperties();
        private Ip2regionProperties ip2region = new Ip2regionProperties();
    }

    @Test
    void applicationYml_everyIntegrationKeyBinds() throws Exception {
        assumeTrue(Files.exists(APP_YML), "找不到 zhimu-app 的 application.yml");
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load("app", new FileSystemResource(APP_YML));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        Root root = binder.bind("zhimu.integration", Bindable.of(Root.class), new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).get();

        AiProperties ai = root.getAi();
        assertThat(ai.isEnabled()).isTrue();
        assertThat(ai.getMaxConcurrency()).isEqualTo(20);
        assertThat(ai.getPrimary().getName()).isEqualTo("dashscope");
        assertThat(ai.getPrimary().getBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(ai.getPrimary().getChatModel()).isEqualTo("qwen3.8-max");
        assertThat(ai.getPrimary().getVisionModel()).isEqualTo("qwen3.8-max");
        assertThat(ai.getPrimary().getAsrModel()).isEqualTo("qwen3-asr-flash");
        assertThat(ai.getFallback().getName()).isEqualTo("deepseek");
        assertThat(ai.getFallback().getChatModel()).isEqualTo("deepseek-flash");
        assertThat(ai.getFallback().getVisionModel()).isEqualTo("deepseek-flash");
        assertThat(ai.getDiagnosisTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(ai.getChatTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(ai.getAsrTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(ai.getAdviceTimeout()).isEqualTo(Duration.ofSeconds(60));

        SmsProperties sms = root.getSms();
        assertThat(sms.getMode()).isEqualTo(SmsMode.MOCK);
        assertThat(sms.getMockCode()).isEqualTo("123456");
        assertThat(sms.getEndpoint()).isEqualTo("dypnsapi.aliyuncs.com");
        assertThat(sms.getCodeLength()).isEqualTo(6);
        assertThat(sms.getValidSeconds()).isEqualTo(300);
        assertThat(sms.getIntervalSeconds()).isEqualTo(60);
        assertThat(sms.getTemplates()).containsEntry("LOGIN_REGISTER", "100001")
                .containsEntry("VERIFY_BOUND_PHONE", "100005").hasSize(5);

        assertThat(root.getPush().getBaseUrl()).isEqualTo("https://api.jpush.cn");
        assertThat(root.getPush().getIntentAction()).isEqualTo("com.zhimu.slmp.action.PUSH_OPEN");
        assertThat(root.getPush().isApnsProduction()).isFalse();
        assertThat(root.getAmap().getBaseUrl()).isEqualTo("https://restapi.amap.com");

        MinioProperties minio = root.getMinio();
        assertThat(minio.getPrivateBucket()).isEqualTo("zmt-private");
        assertThat(minio.getCourseBucket()).isEqualTo("zmt-course");
        assertThat(minio.getPresignTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(minio.getExportPresignTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(minio.getMultipartPartSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(root.getHls().getBaseUrl()).isEqualTo("http://127.0.0.1:9000/zmt-course/hls");
        assertThat(root.getFfmpeg().getTranscodeThreads()).isEqualTo(2);
        assertThat(root.getIp2region().getDbPath()).isEmpty();
    }

    @Test
    void context_startsWithoutAnyKey_andKeepsApplicationTaskExecutor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withUserConfiguration(AiConfig.class, SmsConfig.class, PushConfig.class, AmapConfig.class,
                        StorageConfig.class, MediaConfig.class, IpRegionConfig.class)
                .withPropertyValues("zhimu.integration.minio.endpoint=http://127.0.0.1:1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(AiGateway.class).hasSingleBean(SmsClient.class)
                            .hasSingleBean(PushClient.class).hasSingleBean(AmapClient.class)
                            .hasSingleBean(ObjectStorage.class).hasSingleBean(MediaProcessor.class)
                            .hasSingleBean(ImageProcessor.class).hasSingleBean(IpRegionResolver.class);
                    assertThat(ctx.getBean(AiGateway.class).isEnabled()).isFalse();
                    assertThat(ctx.getBean(SmsClient.class).mode()).isEqualTo(SmsMode.MOCK);
                    assertThat(ctx.getBean(PushClient.class).isEnabled()).isFalse();
                    assertThat(ctx.getBean(IpRegionResolver.class).resolve("8.8.8.8")).isNull();
                    assertThat(ctx.containsBean(MediaConfig.TRANSCODE_EXECUTOR)).isTrue();
                    assertThat(ctx.getBean(MediaConfig.TRANSCODE_EXECUTOR, Executor.class)).isNotNull();
                    assertThat(ctx.containsBean("applicationTaskExecutor")).isTrue();
                });
    }
}
