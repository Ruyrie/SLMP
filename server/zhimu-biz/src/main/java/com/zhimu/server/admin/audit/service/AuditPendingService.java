package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.vo.PendingCountVO;

/** 四类待审核数（ADM-R01）与平台概览待处理卡。只统计当前管理员有 VIEW 权限的队列。 */
public interface AuditPendingService {

    PendingCountVO pendingCounts();

    /** 平台概览：返回全部四类计数（内容运营无审核页权限时也要显示数字） */
    PendingCountVO overviewPendingCounts();
}
