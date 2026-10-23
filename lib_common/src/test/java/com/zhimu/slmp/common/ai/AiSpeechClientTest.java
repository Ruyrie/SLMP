package com.zhimu.slmp.common.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 语音识别直连：input_audio 请求体、按实测响应取 choices[0].message.content、空结果为“没有听清”、HTTP 错误归类。
 */
public class AiSpeechClientTest {

    private static final String RESPONSE = "{\"choices\":[{\"finish_reason\":\"stop\",\"index\":0,\"message\":{"
            + "\"annotations\":[{\"emotion\":\"neutral\",\"language\":\"zh\",\"type\":\"audio_info\"}],"
            + "\"content\":\"保育猪换料后拉稀，要停料吗？\",\"role\":\"assistant\"}}],\"model\":\"qwen3-asr-flash\","
            + "\"object\":\"chat.completion\",\"usage\":{\"seconds\":4}}";

    private static AiSpeechClient client(int code, String body) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                        .body(ResponseBody.create(body, MediaType.get("application/json"))).build()).build();
        return new AiSpeechClient(http, new AiProvider(AiProvider.DASHSCOPE, "百炼语音识别",
                AiProvider.DASHSCOPE_BASE_URL, AiSpeechClient.DEFAULT_MODEL, "test-key"));
    }

    @Test
    public void buildBody_inputAudioDataUrl() {
        JsonObject body = AiSpeechClient.buildBody("qwen3-asr-flash", "data:audio/wav;base64,AAAA");
        assertEquals("qwen3-asr-flash", body.get("model").getAsString());
        assertFalse(body.get("stream").getAsBoolean());
        JsonObject part = body.getAsJsonArray("messages").get(0).getAsJsonObject()
                .getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("input_audio", part.get("type").getAsString());
        assertEquals("data:audio/wav;base64,AAAA", part.getAsJsonObject("input_audio").get("data").getAsString());
        assertTrue(body.getAsJsonObject("asr_options").has("enable_itn"));
    }

    @Test
    public void recognize_parsesContent() throws AiException {
        AiSpeechResult r = client(200, RESPONSE).recognizeBlocking(new byte[]{1, 2}, "audio/wav");
        assertTrue(r.isRecognized());
        assertEquals("保育猪换料后拉稀，要停料吗？", r.getText());
    }

    @Test
    public void recognize_emptyContentMeansNotHeard() throws AiException {
        String empty = "{\"choices\":[{\"message\":{\"content\":\"  \",\"role\":\"assistant\"}}]}";
        assertFalse(client(200, empty).recognizeBlocking(new byte[]{1}, "audio/wav").isRecognized());
    }

    @Test
    public void recognize_httpErrorKinds() {
        try {
            client(503, "{}").recognizeBlocking(new byte[]{1}, "audio/wav");
        } catch (AiException e) {
            assertEquals(AiException.Kind.SERVER, e.getKind());
        }
        try {
            client(401, "{}").recognizeBlocking(new byte[]{1}, "audio/wav");
        } catch (AiException e) {
            assertEquals(AiException.Kind.CLIENT, e.getKind());
        }
        assertEquals("audio/mp4", AiSpeechClient.mimeOf("a.M4A"));
        assertEquals("audio/wav", AiSpeechClient.mimeOf("voice_1.wav"));
    }

    @Test
    public void disabledWithoutProvider() {
        AiSpeechClient c = new AiSpeechClient(new OkHttpClient(), null);
        assertFalse(c.isEnabled());
        try {
            c.recognizeBlocking(new byte[]{1}, "audio/wav");
        } catch (AiException e) {
            assertEquals(AiException.Kind.DISABLED, e.getKind());
        }
    }
}
