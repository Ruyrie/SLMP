package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.request.AccountRecoveryRequest;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.common.enums.ClientType;

/** API-A10 凭恢复码提交找回申请（客服代登记也走同一核对流程）。 */
public interface AccountRecoveryService {

    AccountRecoverySubmitVO submit(AccountRecoveryRequest request, ClientType client);
}
