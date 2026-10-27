package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;

/** Public progress of actual server work. This never contains provider reasoning or internal prompts. */
public final class StreamProgressVO {
    private String stage;
    private String message;

    public StreamProgressVO(@NonNull String stage, @NonNull String message) {
        this.stage = stage;
        this.message = message;
    }

    @NonNull public String getStage() { return stage == null ? "" : stage; }
    @NonNull public String getMessage() { return message == null ? "" : message; }
}
