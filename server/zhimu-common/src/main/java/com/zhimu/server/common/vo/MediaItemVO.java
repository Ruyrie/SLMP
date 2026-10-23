package com.zhimu.server.common.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/** 记录中的照片和视频：FileVO 全部字段 + 排序、来源说明（例如“巡检”）、拍摄人、拍摄时间 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MediaItemVO extends FileVO {
    private Integer sortNo;
    private String sourceText;
    private String shotBy;
    private LocalDateTime shotAt;
}