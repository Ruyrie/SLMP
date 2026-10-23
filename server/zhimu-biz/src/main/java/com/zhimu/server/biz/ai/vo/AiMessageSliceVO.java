package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API-K03 按轮次切片。
 */
@Data
public class AiMessageSliceVO {

    private List<AiMessageVO> list = new ArrayList<>();
    private boolean hasMore;
}
