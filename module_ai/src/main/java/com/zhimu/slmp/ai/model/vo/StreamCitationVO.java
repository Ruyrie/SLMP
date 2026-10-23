package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * SSE citation：结束前发送一次
 */
public class StreamCitationVO {

    private List<CitationBriefVO> citations;

    @NonNull
    public List<CitationBriefVO> getCitations() {
        return citations == null ? Collections.emptyList() : citations;
    }

    public void setCitations(@Nullable List<CitationBriefVO> citations) {
        this.citations = citations;
    }
}
