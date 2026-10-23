package com.zhimu.server.framework.oplog;

/**
 * 写操作留痕。实现类写 t_operation_log（公共能力 biz.common 实现）；
 * 没有实现时框架只把留痕写到应用日志。业务事务内调用时与业务同一事务提交。
 */
public interface OperationLogRecorder {

    void record(OperationLogEntry entry);
}