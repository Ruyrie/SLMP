package com.zhimu.server.integration;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGatewayImpl;
import com.zhimu.server.integration.ai.AiMessage;
import com.zhimu.server.integration.ai.AiProperties;
import com.zhimu.server.integration.ai.AiScene;
import com.zhimu.server.integration.ai.AiStreamHandler;
import com.zhimu.server.integration.amap.AmapAddress;
import com.zhimu.server.integration.amap.AmapClientImpl;
import com.zhimu.server.integration.amap.AmapDistrict;
import com.zhimu.server.integration.amap.AmapProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实服务冒烟（默认不运行）：设置环境变量 ZMT_LIVE_TEST=true 后执行。
 * Key 从环境变量或 server/.env.local 读取，不输出 Key；不发短信、不推送。
 */
@EnabledIfEnvironmentVariable(named = "ZMT_LIVE_TEST", matches = "true")
class LiveSmokeTest {

    private static final Map<String, String> ENV = new HashMap<>();

    @BeforeAll
    static void loadEnv() throws Exception {
        for (Path p : List.of(Path.of("..", ".env.local"), Path.of(".env.local"))) {
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    int eq = s.indexOf('=');
                    if (!s.startsWith("#") && eq > 0) {
                        ENV.putIfAbsent(s.substring(0, eq).trim(), s.substring(eq + 1).trim());
                    }
                }
                break;
            }
        }
        System.getenv().forEach(ENV::put);
    }

    private static String env(String name, String def) {
        String v = ENV.get(name);
        return v == null || v.isBlank() ? def : v;
    }

    private static AiProperties.Provider dashscope() {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setName("dashscope");
        p.setBaseUrl(env("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        p.setApiKey(env("DASHSCOPE_API_KEY", null));
        p.setChatModel(env("DASHSCOPE_CHAT_MODEL", "qwen3.8-max"));
        p.setVisionModel(env("DASHSCOPE_VISION_MODEL", "qwen3.8-max"));
        p.setAsrModel(env("DASHSCOPE_ASR_MODEL", "qwen3-asr-flash"));
        return p;
    }

    private static AiProperties.Provider deepseek() {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setName("deepseek");
        p.setBaseUrl(env("DEEPSEEK_BASE_URL", "https://api.deepseek.com"));
        p.setApiKey(env("DEEPSEEK_API_KEY", null));
        p.setChatModel(env("DEEPSEEK_CHAT_MODEL", "deepseek-flash"));
        p.setVisionModel(env("DEEPSEEK_VISION_MODEL", "deepseek-flash"));
        return p;
    }

    /** 只配置一个服务商，直接验证该服务商 */
    private static AiGatewayImpl only(AiProperties.Provider provider) {
        assumeTrue(provider.available(), provider.getName() + " Key 未配置");
        AiProperties props = new AiProperties();
        props.setPrimary(provider);
        props.setFallback(new AiProperties.Provider());
        return new AiGatewayImpl(props);
    }

    private static String redSquareDataUrl() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private static AiChatRequest textRequest() {
        return AiChatRequest.builder().scene(AiScene.CHAT)
                .message(AiMessage.system("你是畜牧兽医助手，回答简洁。"))
                .message(AiMessage.user("用一句话回答：健康育肥猪的正常体温大约是多少摄氏度？"))
                .maxTokens(1500)
                .build();
    }

    private static AiChatRequest visionRequest() throws Exception {
        return AiChatRequest.builder().scene(AiScene.DIAGNOSIS)
                .message(AiMessage.user("这张图片主要是什么颜色？只回答颜色。", List.of(redSquareDataUrl())))
                .maxTokens(1500)
                .build();
    }

    private static void report(String name, AiChatResult r) {
        String text = r.getContent().length() > 60 ? r.getContent().substring(0, 60) + "…" : r.getContent();
        System.out.println("[LIVE] " + name + " provider=" + r.getProvider() + " model=" + r.getModel()
                + " costMs=" + r.getCostMs() + " usage=" + r.getUsage() + " 回答=" + text.replace('\n', ' '));
    }

    private static AiChatResult call(String name, AiGatewayImpl gw, AiChatRequest req) {
        try {
            AiChatResult r = gw.chat(req);
            report(name, r);
            return r;
        } catch (BizException e) {
            System.out.println("[LIVE] " + name + " 失败 code=" + e.getCode() + " " + e.getMessage());
            throw e;
        }
    }

    @Test
    void dashscope_text() {
        AiChatResult r = call("百炼文本", only(dashscope()), textRequest());
        assertThat(r.getContent()).isNotBlank();
    }

    @Test
    void dashscope_vision() throws Exception {
        AiChatResult r = call("百炼识图", only(dashscope()), visionRequest());
        assertThat(r.getContent()).contains("红");
    }

    @Test
    void deepseek_text() {
        AiChatResult r = call("DeepSeek文本", only(deepseek()), textRequest());
        assertThat(r.getContent()).isNotBlank();
    }

    @Test
    void deepseek_vision() throws Exception {
        AiChatResult r = call("DeepSeek识图", only(deepseek()), visionRequest());
        assertThat(r.getContent()).contains("红");
    }

    @Test
    void dashscope_stream() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> full = new AtomicReference<>();
        AtomicReference<AiChatResult> result = new AtomicReference<>();
        AtomicReference<BizException> error = new AtomicReference<>();
        StringBuilder deltas = new StringBuilder();
        only(dashscope()).stream(textRequest(), new AiStreamHandler() {
            @Override
            public void onDelta(String text) {
                deltas.append(text);
            }

            @Override
            public void onResult(AiChatResult r) {
                result.set(r);
            }

            @Override
            public void onComplete(String finishReason, String fullText) {
                full.set(fullText);
                done.countDown();
            }

            @Override
            public void onError(BizException e) {
                error.set(e);
                done.countDown();
            }
        });
        assertThat(done.await(150, TimeUnit.SECONDS)).isTrue();
        if (error.get() != null) {
            System.out.println("[LIVE] 百炼流式 失败 code=" + error.get().getCode());
        }
        assertThat(error.get()).isNull();
        report("百炼流式", result.get());
        assertThat(full.get()).isEqualTo(deltas.toString()).isNotBlank();
    }

    @Test
    void dashscope_asr() throws Exception {
        Path wav = synthesizeSpeech("保育猪换料后拉稀，要停料吗");
        assumeTrue(wav != null, "无法生成测试录音（需要 Windows 中文语音），跳过");
        try {
            String text = only(dashscope()).recognizeSpeech(Files.readAllBytes(wav), "audio/wav");
            System.out.println("[LIVE] 百炼语音识别 model=" + dashscope().getAsrModel() + " bytes=" + Files.size(wav)
                    + " 识别=" + text);
            assertThat(text).contains("换料");
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void amap_reverseGeocodeAndDistrict() {
        AmapProperties props = new AmapProperties();
        props.setWebKey(env("AMAP_WEB_KEY", null));
        assumeTrue(props.configured(), "AMAP_WEB_KEY 未配置");
        AmapClientImpl client = new AmapClientImpl(props);
        // 东山猪场（示例数据 1001，赤峰市松山区太平地镇）
        AmapAddress a = client.reverseGeocode(118.8516, 42.3025);
        System.out.println("[LIVE] 高德逆地理 " + a);
        assertThat(a).isNotNull();
        assertThat(a.province()).isNotBlank();
        assertThat(a.adcode()).isNotBlank();
        List<AmapDistrict> d = client.districts("内蒙古", 1);
        System.out.println("[LIVE] 高德行政区划 " + d.getFirst().name() + " 下级=" + d.getFirst().children().size());
        assertThat(d).isNotEmpty();
    }

    /** 用 Windows 自带的中文语音合成 16kHz 单声道 WAV；失败返回 null */
    private static Path synthesizeSpeech(String text) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return null;
        }
        try {
            Path wav = Files.createTempFile("zmt-asr-", ".wav");
            String script = "Add-Type -AssemblyName System.Speech;"
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                    + "$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'zh-CN' } | Select-Object -First 1;"
                    + "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) };"
                    + "$f = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, "
                    + "[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono);"
                    + "$s.SetOutputToWaveFile('" + wav.toAbsolutePath() + "', $f);"
                    + "$s.Speak('" + text + "');$s.Dispose()";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0 || Files.size(wav) < 1000) {
                Files.deleteIfExists(wav);
                return null;
            }
            return wav;
        } catch (Exception e) {
            return null;
        }
    }
}
