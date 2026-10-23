package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.request.ProfileUpdateRequest;
import com.zhimu.server.biz.account.vo.AccountOverviewVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.common.enums.ClientType;

/** API-A15 我的页汇总、API-A16 修改姓名。 */
public interface AccountService {

    AccountOverviewVO overview(Long userId, Long farmId, ClientType client);

    ProfileVO updateProfile(ProfileUpdateRequest request);
}
