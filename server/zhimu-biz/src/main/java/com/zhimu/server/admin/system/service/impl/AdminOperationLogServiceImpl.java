package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端操作留痕写入：转成框架 OperationLogEntry 交给 biz.common 的 OperationLogRecorder 落 t_operation_log。
 * operator_type 固定 ADMIN，操作人、客户端、IP 与 IP 属地由记录器补齐。
 */
@Service
@RequiredArgsConstructor
public class AdminOperationLogServiceImpl implements OperationLogService {

    private final OperationLogRecorder operationLogRecorder;

    @Override
    public void writeAdminLog(AdminLogCommand command) {
        if (command == null) {
            return;
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .opType(command.getOpType())
                .opAction(command.getOpAction())
                .result(command.getResult() == null ? "SUCCESS" : command.getResult())
                .bizType(command.getBizType())
                .bizId(command.getBizId())
                .targetName(command.getTargetName())
                .farmId(command.getFarmId())
                .searchText(command.getSearchText())
                .reason(command.getReason())
                .extraText(command.getExtraText())
                .notifyText(command.getNotifyText())
                .before(command.getBefore())
                .after(command.getAfter())
                .build());
    }

    @Override
    public void writeAdminLog(OperationLogEntry entry) {
        if (entry == null) {
            return;
        }
        if (entry.getOperatorType() == null) {
            entry.setOperatorType("ADMIN");
        }
        operationLogRecorder.record(entry);
    }
}
