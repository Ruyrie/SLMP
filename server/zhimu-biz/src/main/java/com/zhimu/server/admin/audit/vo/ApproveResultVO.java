package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 修改申请通过后的重算结果（ADM-R12）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveResultVO {

    private Integer recalcCount;
    private String recalcSummary;
    @Builder.Default
    private List<String> notifiedNames = new ArrayList<>();
}
