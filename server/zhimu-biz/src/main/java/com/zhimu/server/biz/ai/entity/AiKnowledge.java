package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 知识条目（t_ai_knowledge）。管理端-02 未落地前由本模块维护只读内容。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge")
public class AiKnowledge extends BaseEntity {

    private String title;
    private String categoryName;
    private String sourceType;
    private String displayType;
    private Integer isMajorEpidemic;
    private String status;
    private Integer versionNo;
    private String shareUrl;
    private Long embeddedCourseId;
    private Integer sortNo;
    private LocalDateTime publishedAt;
}
