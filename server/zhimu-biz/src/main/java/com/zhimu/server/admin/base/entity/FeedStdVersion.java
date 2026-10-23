package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 饲喂标准版本（t_feed_std_version）：草稿/线上/历史。 */
@Getter
@Setter
@TableName("t_feed_std_version")
public class FeedStdVersion extends BaseAdminEntity {

    private Long breedId;
    private Integer versionNo;
    /** VERSION_STATUS：DRAFT / PUBLISHED / SUPERSEDED */
    private String status;
    private Integer draftFlag;
    private BigDecimal deviationRate;
    private String updateNote;
    private Integer rollbackFromNo;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
