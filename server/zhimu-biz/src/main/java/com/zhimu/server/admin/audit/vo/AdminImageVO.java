package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核资质图（注册审核、资料变更共用）。
 * flag 用于资料变更：KEEP 保留 / NEW 新增 / REMOVED 已删除；注册审核用 reshoot、same_as_last、need_reshoot。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminImageVO {

    private Long fileId;
    private String url;
    private String thumbUrl;
    private Integer sortNo;
    private Boolean reshoot;
    private Boolean sameAsLast;
    private Boolean needReshoot;
    private String flag;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String uploaderName;
}
