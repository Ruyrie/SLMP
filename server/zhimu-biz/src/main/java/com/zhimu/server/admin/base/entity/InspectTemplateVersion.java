package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 巡检模板版本（t_inspect_template_version）：草稿/线上/历史。 */
@Getter
@Setter
@TableName("t_inspect_template_version")
public class InspectTemplateVersion extends BaseAdminEntity {

    private Long templateId;
    private Integer versionNo;
    private String status;
    private Integer draftFlag;
    private String changeNote;
    private Integer itemCount;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
