package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 养殖场切换器与“全部养殖场”汇总中的养殖场 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmBriefVO {
    private Long farmId;
    private String name;
    private String speciesText;
    private String status;
    private String statusText;
    private String role;
}