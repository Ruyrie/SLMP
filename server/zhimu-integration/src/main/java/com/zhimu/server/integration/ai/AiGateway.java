package com.zhimu.server.integration.ai;

/**
 * AI 网关（04 号 9.1 节）：OpenAI 兼容的 Chat Completions，主用百炼、备用 DeepSeek。
 * <ul>
 *     <li>主模型未输出正文前失败（网络错误、5xx、429、空回答）时切换备用；已输出正文后中断不再重试；</li>
 *     <li>请求带图片时用识图模型；只取 content，忽略推理模型的 reasoning_content；</li>
 *     <li>全局并发上限 max-concurrency，排队超过 10 秒返回 50201；两个服务商都失败 50201，超时 50401；</li>
 *     <li>日志只记场景、服务商、模型、耗时、token 数与结果，不记提示词与图片。</li>
 * </ul>
 * 发给模型的内容不要包含手机号、姓名等个人信息；禁用药比对由业务模块实现 {@link BannedDrugGuard} 后自行调用。
 */
public interface AiGateway {

    /** 是否至少配置了一个服务商的 Key */
    boolean isEnabled();

    /** 非流式调用（阻塞，建议在虚拟线程中调用） */
    AiChatResult chat(AiChatRequest request);

    /**
     * 流式调用：立即返回，结果通过 handler 回调。网关未开启、排队超时也通过 onError 通知（在调用线程上回调）。
     * 排队等待并发名额时会阻塞调用线程（最多 10 秒）。
     */
    AiStreamHandle stream(AiChatRequest request, AiStreamHandler handler);

    /**
     * 语音转文字（qwen3-asr-flash，只走主服务商）。
     *
     * @param audio    录音（wav、m4a、mp3），不超过 10MB
     * @param mimeType 例如 audio/wav、audio/mp4
     * @return 识别文字；没有识别出内容时为空串（由调用方提示“没有听清，请再说一遍”）
     */
    String recognizeSpeech(byte[] audio, String mimeType);
}
