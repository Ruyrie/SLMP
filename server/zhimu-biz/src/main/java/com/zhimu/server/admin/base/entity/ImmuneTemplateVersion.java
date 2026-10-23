package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 免疫模板版本（t_immune_template_version）：草稿/线上/历史。
 * 草稿 version_no 为空、draft_flag=1；发布后写 version_no 与 published_at。
 */
@Getter
@Setter
@TableName("t_immune_template_version")
public class ImmuneTemplateVersion extends BaseAdminEntity {

    private Long templateId;
    private Integer versionNo;
    /** VERSION_STATUS：DRAFT / PUBLISHED / SUPERSEDED */
    private String status;
    /** 草稿为 1，其余为空 */
    private Integer draftFlag;
    private String changeNote;
    private Integer itemCount;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
