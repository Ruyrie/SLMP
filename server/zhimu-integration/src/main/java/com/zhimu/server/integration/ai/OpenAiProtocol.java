package com.zhimu.server.integration.ai;

import com.zhimu.server.integration.support.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI 兼容 Chat Completions 的请求体与响应解析（与 App 的 AiChatClient、AiSpeechClient 报文一致）。
 */
final class OpenAiProtocol {

    private OpenAiProtocol() {
    }

    static ObjectNode chatBody(String model, AiChatRequest req, boolean stream) {
        ObjectNode root = Jsons.object();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        for (AiMessage m : req.getMessages()) {
            ObjectNode node = messages.addObject();
            node.put("role", m.getRole());
            if (!m.hasImages()) {
                node.put("content", m.getText());
                continue;
            }
            ArrayNode parts = node.putArray("content");
            if (!m.getText().isEmpty()) {
                parts.addObject().put("type", "text").put("text", m.getText());
            }
            for (String image : m.getImages()) {
                ObjectNode part = parts.addObject();
                part.put("type", "image_url");
                part.putObject("image_url").put("url", image);
            }
        }
        root.put("stream", stream);
        if (stream) {
            root.putObject("stream_options").put("include_usage", true);
        }
        if (req.getMaxTokens() != null) {
            root.put("max_tokens", req.getMaxTokens());
        }
        if (req.getTemperature() != null) {
            root.put("temperature", req.getTemperature());
        }
        if (req.isJsonMode()) {
            root.putObject("response_format").put("type", "json_object");
        }
        return root;
    }

    /** 语音识别：音频以 data URL 放在 input_audio.data，数字按阿拉伯数字输出（enable_itn） */
    static ObjectNode asrBody(String model, String audioDataUrl) {
        ObjectNode root = Jsons.object();
        root.put("model", model);
        ObjectNode user = root.putArray("messages").addObject();
        user.put("role", "user");
        ObjectNode part = user.putArray("content").addObject();
        part.put("type", "input_audio");
        part.putObject("input_audio").put("data", audioDataUrl);
        root.put("stream", false);
        root.putObject("asr_options").put("enable_itn", true);
        return root;
    }

    /** 非流式响应；content 可能是字符串或 text 片段数组 */
    record Completion(String content, String finishReason, String model, AiUsage usage) {
    }

    /** 解析失败返回 null */
    static Completion parseCompletion(String raw) {
        JsonNode root = Jsons.parse(raw);
        if (root == null || !root.isObject()) {
            return null;
        }
        String content = null;
        String finish = null;
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            finish = Jsons.text(first, "finish_reason");
            JsonNode message = first.get("message");
            if (message != null && message.isObject()) {
                content = contentText(message.get("content"));
            }
        }
        return new Completion(content, finish, Jsons.text(root, "model"), usage(root.get("usage")));
    }

    /** 流式响应中的一个 data 块 */
    record Delta(String content, String finishReason, String model, AiUsage usage, String error) {
    }

    /**
     * 解析一个 chat.completion.chunk：取 choices[0].delta.content 与 finish_reason，忽略 reasoning_content；
     * 最后的 usage 块 choices 为空；流中途的 {"error": {...}} 记为 error。解析失败返回 null。
     */
    static Delta parseDelta(String data) {
        JsonNode root = Jsons.parse(data);
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode err = root.get("error");
        if (err != null && !err.isNull()) {
            String code = err.isObject() ? firstNonNull(Jsons.text(err, "code"), Jsons.text(err, "type")) : err.asString();
            return new Delta(null, null, null, null, code == null ? "error" : code);
        }
        String content = null;
        String finish = null;
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            finish = Jsons.text(first, "finish_reason");
            JsonNode delta = first.get("delta");
            if (delta != null && delta.isObject()) {
                content = contentText(delta.get("content"));
            }
        }
        return new Delta(content, finish, Jsons.text(root, "model"), usage(root.get("usage")), null);
    }

    /** 错误响应体中的说明（error.code / error.message），用于日志 */
    static String errorSummary(String raw) {
        JsonNode root = Jsons.parse(raw);
        if (root != null && root.isObject()) {
            JsonNode err = root.get("error");
            if (err != null && err.isObject()) {
                String code = Jsons.text(err, "code");
                String msg = Jsons.text(err, "message");
                return Jsons.abbreviate((code == null ? "" : code + " ") + (msg == null ? "" : msg), 200);
            }
            String code = Jsons.text(root, "code");
            String msg = Jsons.text(root, "message");
            if (code != null || msg != null) {
                return Jsons.abbreviate((code == null ? "" : code + " ") + (msg == null ? "" : msg), 200);
            }
        }
        return Jsons.abbreviate(raw, 200);
    }

    private static String contentText(JsonNode c) {
        if (c == null || c.isNull()) {
            return null;
        }
        if (c.isString()) {
            return c.stringValue();
        }
        if (c.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : c) {
                if (part.isString()) {
                    sb.append(part.stringValue());
                } else if (part.isObject() && part.get("text") != null) {
                    sb.append(part.get("text").asString());
                }
            }
            return sb.toString();
        }
        return null;
    }

    private static AiUsage usage(JsonNode u) {
        if (u == null || !u.isObject()) {
            return null;
        }
        return new AiUsage(Jsons.integer(u, "prompt_tokens"), Jsons.integer(u, "completion_tokens"),
                Jsons.integer(u, "total_tokens"));
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
