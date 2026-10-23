package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 记录中的批次；day_age 由后端按入栏日龄和今天计算 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchBriefVO {
    private Long batchId;
    private String batchNo;
    private String breedText;
    private Integer dayAge;
    private Integer headCount;
    private String penName;
}