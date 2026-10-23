package com.zhimu.server.biz.farm.api;

import java.time.LocalDateTime;

/**
 * 注销预览中本人为所有者的养殖场（对应 CancelOwnedFarmVO）。
 *
 * @param farmStatus            FARM_STATUS 编码
 * @param currentStock          总存栏（正常、停用时有值，其他为 null）
 * @param memberCount           除本人外的有效成员数
 * @param transferable          正常、至少 1 名场长、没有待确认转让
 * @param transferPendingToName 待确认转让的受让人姓名，可空
 * @param disabledAt            停用时间（仅已停用有值，用于计算 180 天后清理日），可空
 */
public record CancelOwnedFarm(Long farmId, String name, String farmStatus, String farmStatusText,
                              Integer currentStock, String countUnitText, int memberCount,
                              CancelFarmDisposal disposal, boolean transferable, String transferPendingToName,
                              LocalDateTime disabledAt) {
}