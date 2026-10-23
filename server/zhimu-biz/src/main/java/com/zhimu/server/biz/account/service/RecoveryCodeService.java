package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.request.RecoveryCodeRegenerateRequest;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.enums.ClientType;

/** API-A12–A14 恢复码的重新生成与确认。 */
public interface RecoveryCodeService {

    RecoveryCodeVO regeneratePending(ClientType client);

    RecoveryCodeVO regenerateBySms(RecoveryCodeRegenerateRequest request, ClientType client);

    RecoveryCodeConfirmVO confirm(Long recoveryCodeId);
}
