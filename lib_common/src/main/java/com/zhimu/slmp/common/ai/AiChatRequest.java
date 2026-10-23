package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次 Chat Completions 请求：系统提示词、用户文字、图片（按顺序放在文字之后）与输出长度。
 * <pre>
 * AiChatRequest req = AiChatRequest.builder()
 *         .system("你是畜牧兽医助手……只输出 JSON")
 *         .user("栏舍：3 号育肥舍……")
 *         .image(AiImage.fromBitmap(bitmap, 1024, 80, "照片 1"))
 *         .maxTokens(2000)
 *         .build();
 * </pre>
 */
public final class AiChatRequest {

    public static final int DEFAULT_MAX_TOKENS = 2048;

    @Nullable
    private final String system;
    @NonNull
    private final String user;
    @NonNull
    private final List<AiImage> images;
    private final int maxTokens;
    @Nullable
    private final Double temperature;
    @NonNull
    private final String tag;
    /** 多轮对话的历史消息（模块11 追加，默认为空，不影响单轮调用） */
    @NonNull
    private final List<AiTurn> history;

    private AiChatRequest(@NonNull Builder b) {
        this.system = b.system;
        this.user = b.user == null ? "" : b.user;
        this.images = Collections.unmodifiableList(new ArrayList<>(b.images));
        this.history = Collections.unmodifiableList(new ArrayList<>(b.history));
        this.maxTokens = b.maxTokens;
        this.temperature = b.temperature;
        this.tag = b.tag;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getSystem() {
        return system;
    }

    @NonNull
    public String getUser() {
        return user;
    }

    @NonNull
    public List<AiImage> getImages() {
        return images;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    @Nullable
    public Double getTemperature() {
        return temperature;
    }

    /** 日志中的用途标记，例如“diagnosis” */
    @NonNull
    public String getTag() {
        return tag;
    }

    /** 历史消息（按时间顺序，放在本次提问之前） */
    @NonNull
    public List<AiTurn> getHistory() {
        return history;
    }

    /** 图片总字节数（日志用） */
    public int imageBytes() {
        int n = 0;
        for (AiImage image : images) {
            n += image.getByteCount();
        }
        return n;
    }

    public static final class Builder {
        @Nullable
        private String system;
        @Nullable
        private String user;
        private final List<AiImage> images = new ArrayList<>();
        private int maxTokens = DEFAULT_MAX_TOKENS;
        @Nullable
        private Double temperature;
        @NonNull
        private String tag = "chat";
        private final List<AiTurn> history = new ArrayList<>();

        private Builder() {
        }

        /** 追加历史消息（多轮对话；按时间顺序） */
        @NonNull
        public Builder history(@NonNull List<AiTurn> turns) {
            history.addAll(turns);
            return this;
        }

        @NonNull
        public Builder system(@Nullable String system) {
            this.system = system;
            return this;
        }

        @NonNull
        public Builder user(@NonNull String user) {
            this.user = user;
            return this;
        }

        @NonNull
        public Builder image(@NonNull AiImage image) {
            images.add(image);
            return this;
        }

        @NonNull
        public Builder images(@NonNull List<AiImage> list) {
            images.addAll(list);
            return this;
        }

        /** 推理模型的思考也计入输出，建议不少于 1500 */
        @NonNull
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = Math.max(64, maxTokens);
            return this;
        }

        /** 不设时不发送（部分推理模型不支持） */
        @NonNull
        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @NonNull
        public Builder tag(@NonNull String tag) {
            this.tag = tag;
            return this;
        }

        @NonNull
        public AiChatRequest build() {
            return new AiChatRequest(this);
        }
    }
}
