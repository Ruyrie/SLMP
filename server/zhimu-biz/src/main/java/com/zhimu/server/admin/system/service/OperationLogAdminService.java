package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.query.OperationLogQuery;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.common.api.PageVO;

/** 操作日志查询（ADM-S30、S31）：只查 ADMIN / SYSTEM 日志，按查看人权限点过滤。 */
public interface OperationLogAdminService {

    PageVO<OperationLogItemVO> list(OperationLogQuery query);

    /** 不可见的日志按不存在处理（40401） */
    OperationLogDetailVO detail(Long logId);
}
