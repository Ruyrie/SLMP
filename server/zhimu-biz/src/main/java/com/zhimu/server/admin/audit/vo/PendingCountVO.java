package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 四类待审核数（ADM-R01，菜单角标与平台概览）。
 * 没有对应页面 VIEW 权限的队列字段为 null（不输出），items 只含有权限的队列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingCountVO {

    private Integer farmAudit;
    private Integer farmChange;
    private Integer changeRequest;
    private Integer accountRecovery;
    private Integer overdueTotal;
    @Builder.Default
    private List<QueueCountVO> items = new ArrayList<>();
    /** 待审核修改申请中影响可出栏日期的条数 */
    private Integer sellableDateCount;
}
