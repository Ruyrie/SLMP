package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.request.AccountCancelRequest;
import com.zhimu.server.biz.account.vo.CancellationPreviewVO;
import com.zhimu.server.common.enums.ClientType;

/** API-A19 注销预览、API-A20 注销账号。 */
public interface AccountCancelService {

    CancellationPreviewVO preview();

    void cancel(AccountCancelRequest request, ClientType client);
}
