package com.zhimu.server.biz.account.convert;

import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.enums.SmsSendStatus;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;

/** AccountRecovery → VO。用户端状态文字按任务书第七节用“待审核”。 */
public final class AccountRecoveryConvert {

    private AccountRecoveryConvert() {
    }

    public static AccountRecoverySubmitVO toSubmitVO(AccountRecovery recovery) {
        return AccountRecoverySubmitVO.builder()
                .recoveryId(recovery.getId())
                .newPhoneMasked(Masks.phone(recovery.getNewPhone()))
                .status(recovery.getStatus())
                .statusText("待审核")
                .submittedAt(BeijingTime.format(recovery.getCreatedAt()))
                .build();
    }

    public static String pendingSmsStatus() {
        return SmsSendStatus.PENDING.name();
    }
}
