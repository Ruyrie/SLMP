package com.zhimu.server.admin.system.api;

import com.zhimu.server.framework.oplog.OperationLogEntry;

/**
 * 管理端操作留痕写入（管理端-04 的 writeAdminLog，供 admin.audit、admin.content、admin.base 调用）。
 * 在业务事务内调用时与业务同成同败；operator 取 AdminContext，手机号等敏感信息由调用方先打码。
 */
public interface OperationLogService {

    void writeAdminLog(AdminLogCommand command);

    /** 需要直接构造框架留痕对象时使用 */
    void writeAdminLog(OperationLogEntry entry);
}
