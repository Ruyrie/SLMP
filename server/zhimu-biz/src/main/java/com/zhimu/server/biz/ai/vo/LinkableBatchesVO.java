package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API-K05 关联批次面板。
 */
@Data
public class LinkableBatchesVO {

    private List<LinkedBatchVO> batches = new ArrayList<>();
    private String panelHint;
    private String emptyHint;
}
