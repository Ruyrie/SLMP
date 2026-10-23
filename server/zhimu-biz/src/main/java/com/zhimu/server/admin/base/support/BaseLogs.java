package com.zhimu.server.admin.base.support;

import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.oplog.OperationLogSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 管理端基础数据操作留痕：优先交给 biz.common 的 OperationLogRecorder（与业务同一事务），
 * 没有实现时按框架规则写到 OPLOG 日志。
 */
@Component
@RequiredArgsConstructor
public class BaseLogs {

    private final ObjectProvider<OperationLogRecorder> recorderProvider;

    public void record(OperationLogEntry entry) {
        OperationLogEntry filled = OperationLogSupport.fillContext(entry);
        OperationLogRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            OperationLogSupport.writeToLog(filled);
            return;
        }
        recorder.record(filled);
    }
}
