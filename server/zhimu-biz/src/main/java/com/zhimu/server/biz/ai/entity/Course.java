package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 养殖课堂课程（t_course）。用户端模块11 读视频卡片与播放，管理端 C03 补管理字段。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course")
public class Course extends AuditEntity {

    private String title;
    private String intro;
    private Long categoryId;
    private String categoryName;
    private Long topicId;
    private String topicName;
    private Integer sectionNo;
    private Long knowledgeId;
    private Long videoFileId;
    private Integer durationSec;
    private String transcodeStatus;
    private Long coverFileId;
    private String coverSource;
    private String sourceLabel;
    private String coverUrl;
    private String shareUrl;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime onShelfAt;
    private Long onShelfBy;
    private LocalDateTime offShelfAt;
    private Long offShelfBy;
    private String offReason;
    private Integer playCount;
    private Integer viewerCount;
    private Integer finishCount;
    private Long updatedBy;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
