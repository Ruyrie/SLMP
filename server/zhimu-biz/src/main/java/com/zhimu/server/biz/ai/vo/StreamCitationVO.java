package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE citation：结束前发送一次。
 */
@Data
public class StreamCitationVO {

    private List<CitationBriefVO> citations = new ArrayList<>();
}
