package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.query.InviteQuery;
import com.zhimu.server.biz.farm.request.InviteMemberRequest;
import com.zhimu.server.biz.farm.vo.InviteAcceptVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.common.api.PageVO;

/** 邀请列表、发送邀请、同意 / 拒绝（API-F19–F22） */
public interface FarmInviteService {

    PageVO<InviteItemVO> list(Long farmId, Long userId, InviteQuery query);

    InviteItemVO send(Long farmId, Long userId, InviteMemberRequest request);

    InviteAcceptVO accept(Long inviteId, Long userId);

    void reject(Long inviteId, Long userId);
}
