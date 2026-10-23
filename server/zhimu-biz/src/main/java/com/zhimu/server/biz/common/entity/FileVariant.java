package com.zhimu.server.biz.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文件派生版本（t_file_variant，05 号 5.2 节）：图片缩略图（THUMB）与课程 HLS、压缩版。
 * 该表没有 deleted / created_by，只有 id、created_at、updated_at 公共字段。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_file_variant")
public class FileVariant extends BaseEntity {

    private Long fileId;
    private String variantCode;
    private String objectKey;
    private String status;
    private BigDecimal progress;
    private Integer width;
    private Integer height;
    private Integer bitrateKbps;
    private Long fileSize;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
