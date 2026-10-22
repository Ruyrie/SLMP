package com.zhimu.slmp.common.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * 测试直连：请求体、响应解析（忽略 reasoning_content）、错误分类与 JSON 提取。
 */
public class AiChatClientTest {

    @Test
    public void buildBody_textOnly() {
        AiChatRequest req = AiChatRequest.builder().system("你是兽医助手").user("只回答 JSON").maxTokens(300).build();
        JsonObject body = AiChatClient.buildBody("qwen3.8-max", req);
        assertEquals("qwen3.8-max", body.get("model").getAsString());
        assertFalse(body.get("stream").getAsBoolean());
        assertEquals(300, body.get("max_tokens").getAsInt());
        JsonArray messages = body.getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("只回答 JSON", messages.get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void buildBody_imagesAsBase64DataUrl() {
        byte[] jpeg = "fake-jpeg".getBytes(StandardCharsets.UTF_8);
        AiChatRequest req = AiChatRequest.builder().user("看图").image(AiImage.jpeg(jpeg, "照片 1")).build();
        JsonObject body = AiChatClient.buildBody("deepseek-flash", req);
        JsonArray parts = body.getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonArray("content");
        assertEquals(3, parts.size());
        assertEquals("text", parts.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("照片 1", parts.get(1).getAsJsonObject().get("text").getAsString());
        JsonObject image = parts.get(2).getAsJsonObject();
        assertEquals("image_url", image.get("type").getAsString());
        assertEquals("data:image/jpeg;base64,ZmFrZS1qcGVn",
                image.getAsJsonObject("image_url").get("url").getAsString());
        assertEquals(jpeg.length, req.imageBytes());
    }

    @Test
    public void parseResponse_usesContentAndIgnoresReasoning() throws AiException {
        String raw = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"reasoning_content\":\"先分析……\",\"content\":\"{\\\"ok\\\":true}\"}}]}";
        AiChatClient.Parsed p = AiChatClient.parseResponse(raw);
        assertEquals("{\"ok\":true}", p.content);
        assertEquals("stop", p.finishReason);
    }

    @Test
    public void parseResponse_contentParts() throws AiException {
        String raw = "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"a\"},"
                + "{\"type\":\"text\",\"text\":\"b\"}]}}]}";
        assertEquals("ab", AiChatClient.parseResponse(raw).content);
    }

    @Test
    public void parseResponse_emptyOrNull() throws AiException {
        assertNull(AiChatClient.parseResponse("{\"choices\":[]}").content);
        AiChatClient.Parsed p = AiChatClient.parseResponse(
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}]}");
        assertNull(p.content);
        assertEquals("length", p.finishReason);
    }

    @Test
    public void parseResponse_badJson() {
        try {
            AiChatClient.parseResponse("<html>502</html>");
            fail();
        } catch (AiException e) {
            assertEquals(AiException.Kind.BAD_RESPONSE, e.getKind());
            assertFalse(e.isRetryable());
        }
    }

    @Test
    public void errorKinds() {
        assertEquals(AiException.Kind.SERVER, AiException.kindOf(500));
        assertEquals(AiException.Kind.SERVER, AiException.kindOf(503));
        assertEquals(AiException.Kind.SERVER, AiException.kindOf(429));
        assertEquals(AiException.Kind.SERVER, AiException.kindOf(408));
        assertEquals(AiException.Kind.CLIENT, AiException.kindOf(400));
        assertEquals(AiException.Kind.CLIENT, AiException.kindOf(401));
        assertTrue(new AiException(AiException.Kind.NETWORK, -1, "dashscope", "超时", null).isRetryable());
        assertTrue(new AiException(AiException.Kind.SERVER, 502, "dashscope", "5xx", null).isRetryable());
        assertFalse(new AiException(AiException.Kind.CLIENT, 401, "dashscope", "Key 无效", null).isRetryable());
    }

    @Test
    public void provider_disabledWithoutKey_andToStringHidesKey() {
        AiProvider off = new AiProvider(AiProvider.DASHSCOPE, "通义千问", AiProvider.DASHSCOPE_BASE_URL + "/", "qwen", " ");
        assertFalse(off.isEnabled());
        AiProvider on = new AiProvider(AiProvider.DEEPSEEK, "DeepSeek", AiProvider.DEEPSEEK_BASE_URL, "deepseek-flash",
                "sk-test-not-real");
        assertTrue(on.isEnabled());
        assertFalse(on.toString().contains("sk-test-not-real"));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", off.chatCompletionsUrl());
        assertEquals("https://api.deepseek.com/chat/completions", on.chatCompletionsUrl());
    }

    @Test
    public void json_extract() {
        assertNull(AiJson.extractObject(null));
        assertNull(AiJson.extractObject("没有 JSON"));
        JsonObject o = AiJson.extractObject("说明 {\"a\":\"含 } 的文字\",\"b\":[1,2]} 结尾 {\"c\":1}");
        assertNotNull(o);
        assertEquals("含 } 的文字", o.get("a").getAsString());
        assertFalse(o.has("c"));
        JsonObject fenced = AiJson.extractObject("```json\n{\"x\": 1}\n```");
        assertNotNull(fenced);
        assertEquals(1, fenced.get("x").getAsInt());
    }

    @Test
    public void json_truncatedRepair() {
        JsonObject a = AiJson.extractObject("{\"list\":[{\"n\":\"甲\"},{\"n\":\"乙");
        assertNotNull(a);
        assertEquals(2, a.getAsJsonArray("list").size());
        JsonObject b = AiJson.extractObject("{\"x\":1,\"y\":");
        assertNotNull(b);
        assertEquals(1, b.get("x").getAsInt());
        assertFalse(b.has("y"));
        assertEquals("]}", AiJson.closers("{\"a\":[1"));
        assertEquals("\"}", AiJson.closers("{\"a\":\"te"));
        assertEquals(6, AiJson.lastStructuralComma("{\"a\":1,\"b\":\"x,y\""));
    }
}
