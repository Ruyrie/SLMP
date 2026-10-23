package com.zhimu.server.biz.account.convert;

import com.zhimu.server.biz.account.vo.CancelExitFarmVO;
import com.zhimu.server.biz.account.vo.CancelOwnedFarmVO;
import com.zhimu.server.biz.farm.api.CancelExitFarm;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;

/** 模块02 的注销清单 record → 模块01 的 VO。 */
public final class AccountCancelConvert {

    private AccountCancelConvert() {
    }

    public static CancelOwnedFarmVO toOwnedVO(CancelOwnedFarm farm) {
        return CancelOwnedFarmVO.builder()
                .farmId(farm.farmId())
                .name(farm.name())
                .farmStatus(farm.farmStatus())
                .farmStatusText(farm.farmStatusText())
                .disposal(farm.disposal() == null ? null : farm.disposal().name())
                .disposalText(farm.disposal() == null ? null : farm.disposal().getText())
                .currentStock(farm.currentStock())
                .countUnitText(farm.countUnitText())
                .memberCount(farm.memberCount())
                .transferable(farm.transferable())
                .transferPendingToName(farm.transferPendingToName())
                .build();
    }

    public static CancelExitFarmVO toExitVO(CancelExitFarm farm) {
        return CancelExitFarmVO.builder()
                .farmId(farm.farmId())
                .name(farm.name())
                .role(farm.role())
                .roleText(farm.roleText())
                .build();
    }
}
