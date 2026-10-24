package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识条目（t_ai_knowledge）。用户端 K03 读取线上版本；管理端 C04 维护草稿、版本与上下架。
 * 线上标题、分类、关键词等冗余字段由发布 / 回退回写，保证用户端读到的始终是线上版本。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge")
public class AiKnowledge extends AuditEntity {

    private String title;
    private Long categoryId;
    private String categoryName;
    private String sourceType;
    private Long sourceRefId;
    private String displayType;
    private Integer isMajorEpidemic;
    private String status;
    private Integer versionNo;
    private Long onlineVersionId;
    private String keywords;
    private Integer hasVideo;
    private String shareUrl;
    private Long embeddedCourseId;
    private Integer sortNo;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    @TableField("cite_count_30d")
    private Integer citeCount30d;
    @TableField("useful_rate_30d")
    private BigDecimal usefulRate30d;
    private LocalDateTime statAt;
    private String offReason;
    private LocalDateTime offAt;
    private Long offBy;
    private Long updatedBy;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
