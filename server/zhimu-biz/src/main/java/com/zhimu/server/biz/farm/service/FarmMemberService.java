package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.query.MemberQuery;
import com.zhimu.server.biz.farm.request.UpdateMemberPensRequest;
import com.zhimu.server.biz.farm.request.UpdateMemberRoleRequest;
import com.zhimu.server.biz.farm.vo.MemberChangeResultVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberRemoveResultVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.PenOptionVO;
import com.zhimu.server.biz.farm.vo.RemovalImpactVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 成员概况、列表、详情、栏舍选项、改角色、调栏舍、移出、退出（API-F15–F18、F23–F27） */
public interface FarmMemberService {

    MemberSummaryVO summary(Long farmId, Long userId);

    PageVO<MemberItemVO> list(Long farmId, Long userId, MemberQuery query);

    MemberDetailVO detail(Long farmId, Long memberId, Long userId);

    List<PenOptionVO> penOptions(Long farmId, Long memberId, Long userId);

    RemovalImpactVO removalImpact(Long farmId, Long memberId, Long userId);

    MemberChangeResultVO updateRole(Long farmId, Long memberId, Long userId, UpdateMemberRoleRequest request);

    MemberChangeResultVO updatePens(Long farmId, Long memberId, Long userId, UpdateMemberPensRequest request);

    MemberRemoveResultVO remove(Long farmId, Long memberId, Long userId, int version);

    /** 主动退出，返回养殖场名称（用于提示“你已退出东山猪场”） */
    String quit(Long farmId, Long userId);
}
