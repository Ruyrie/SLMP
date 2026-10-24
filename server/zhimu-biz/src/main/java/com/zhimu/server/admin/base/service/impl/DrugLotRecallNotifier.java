package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.query.FarmReceiverRow;
import com.zhimu.server.admin.base.query.IdNameRow;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批号停用通知（管理端-03 2.6（3））：提交后给有结存养殖场的所有者、场长写 LOT_RECALLED 消息。
 * 失败只记日志，不影响停用结果；消息详情按 biz_id = 该场批号 ID 打开 M06。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrugLotRecallNotifier {

    private final ObjectProvider<MessageService> messageServiceProvider;
    private final BaseAdminReadMapper readMapper;

    /** 在事务提交后调用 */
    public void notifyRecalled(Long recallId, String catalogName, String lotNo, String reason,
                               Map<Long, Long> lotIdByFarm) {
        if (lotIdByFarm == null || lotIdByFarm.isEmpty()) {
            return;
        }
        MessageService messageService = messageServiceProvider.getIfAvailable();
        if (messageService == null) {
            log.info("未启用站内消息服务，跳过批号停用通知 recall={} lot={}", recallId, lotNo);
            return;
        }
        try {
            List<Long> farmIds = new ArrayList<>(lotIdByFarm.keySet());
            Map<Long, String> farmNames = new HashMap<>();
            for (IdNameRow row : readMapper.farmNames(farmIds)) {
                farmNames.put(row.getId(), row.getName());
            }
            Map<Long, Set<Long>> receivers = new HashMap<>();
            for (FarmReceiverRow row : readMapper.recallReceivers(farmIds)) {
                receivers.computeIfAbsent(row.getFarmId(), key -> new HashSet<>()).add(row.getUserId());
            }
            for (Map.Entry<Long, Long> entry : lotIdByFarm.entrySet()) {
                Long farmId = entry.getKey();
                Set<Long> userIds = receivers.get(farmId);
                if (userIds == null || userIds.isEmpty()) {
                    continue;
                }
                String farmName = farmNames.get(farmId);
                messageService.send(MessageCommand.builder()
                        .msgType(MsgType.LOT_RECALLED)
                        .level(MsgLevel.IMPORTANT)
                        .farmId(farmId)
                        .receiverUserIds(userIds)
                        .title("批号停用：" + catalogName)
                        .summary((farmName == null ? "" : farmName + " · ") + "批号 " + lotNo)
                        .content(reason)
                        .bizType(BizType.DRUG_LOT)
                        .bizId(entry.getValue())
                        .route("M06")
                        .extra("lot_no", lotNo)
                        .extra("recall_id", recallId)
                        .build());
            }
        } catch (RuntimeException e) {
            log.warn("批号停用通知发送失败 recall={} lot={}", recallId, lotNo, e);
        }
    }
}
