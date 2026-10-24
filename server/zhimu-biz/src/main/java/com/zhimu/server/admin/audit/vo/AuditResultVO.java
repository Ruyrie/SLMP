package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 审核写操作的结果（ADM-R04/R05、R08/R09、R16/R17）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResultVO {

    private String targetName;
    @Builder.Default
    private List<String> notifiedNames = new ArrayList<>();
    /** 找回通过时的新手机号（打码）；其他审核为空 */
    private String smsPhone;
}
