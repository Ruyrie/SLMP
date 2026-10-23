package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 记录中的栏舍 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenBriefVO {
    private Long penId;
    private String name;
    private String penTypeText;
}