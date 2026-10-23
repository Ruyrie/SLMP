package com.zhimu.server.biz.farm.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.zhimu.server.common.error.Asserts.found;

/**
 * 模块02 基础查询：养殖场、在场成员、员工负责栏舍。
 * 所有查询都在 TenantSkip 内并显式带 farm_id / user_id，避免 X-Farm-Id 与路径 farmId 不一致时误过滤。
 */
@Component
@RequiredArgsConstructor
public class FarmLookup {

    private final FarmMapper farmMapper;
    private final FarmMemberMapper memberMapper;
    private final MemberPenMapper memberPenMapper;

    /** 未删除的养殖场；不存在返回 null */
    public Farm findFarm(Long farmId) {
        if (farmId == null) {
            return null;
        }
        return TenantSkip.call(() -> farmMapper.selectById(farmId));
    }

    public Farm requireFarm(Long farmId) {
        return found(findFarm(farmId));
    }

    /** 含已删除的养殖场；用于 F06/F21/F30 区分 41001 */
    public Farm findFarmIncludingDeleted(Long farmId) {
        if (farmId == null) {
            return null;
        }
        return TenantSkip.call(() -> farmMapper.selectIncludingDeleted(farmId));
    }

    /** 已删除返回 41001，不存在返回 40401 */
    public Farm requireNotDeletedFarm(Long farmId) {
        Farm farm = findFarmIncludingDeleted(farmId);
        if (farm == null) {
            throw new com.zhimu.server.common.error.BizException(ErrorCode.NOT_FOUND);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw new com.zhimu.server.common.error.BizException(ErrorCode.RECORD_DELETED,
                    farm.getName() + "已被删除");
        }
        return farm;
    }

    /** 在场成员（active_flag = 1 且 status = ACTIVE）；不是成员返回 null */
    public FarmMember findActiveMember(Long farmId, Long userId) {
        if (farmId == null || userId == null) {
            return null;
        }
        return TenantSkip.call(() -> memberMapper.selectOne(Wrappers.<FarmMember>lambdaQuery()
                .eq(FarmMember::getFarmId, farmId)
                .eq(FarmMember::getUserId, userId)
                .eq(FarmMember::getActiveFlag, true)
                .eq(FarmMember::getStatus, "ACTIVE")
                .last("LIMIT 1")));
    }

    public FarmMember requireActiveMember(Long farmId, Long userId) {
        return found(findActiveMember(farmId, userId));
    }

    public FarmMember findActiveMemberById(Long farmId, Long memberId) {
        if (farmId == null || memberId == null) {
            return null;
        }
        return TenantSkip.call(() -> memberMapper.selectOne(Wrappers.<FarmMember>lambdaQuery()
                .eq(FarmMember::getFarmId, farmId)
                .eq(FarmMember::getId, memberId)
                .eq(FarmMember::getActiveFlag, true)
                .eq(FarmMember::getStatus, "ACTIVE")
                .last("LIMIT 1")));
    }

    /** 养殖场全部在场成员，按角色（所有者在最前）与加入时间排序 */
    public List<FarmMember> activeMembers(Long farmId) {
        return TenantSkip.call(() -> memberMapper.selectList(Wrappers.<FarmMember>lambdaQuery()
                .eq(FarmMember::getFarmId, farmId)
                .eq(FarmMember::getActiveFlag, true)
                .eq(FarmMember::getStatus, "ACTIVE")
                .orderByAsc(FarmMember::getJoinedAt)
                .orderByAsc(FarmMember::getId)));
    }

    /** 员工负责栏舍行 */
    public List<MemberPen> memberPens(Long farmId, Long memberId) {
        return TenantSkip.call(() -> memberPenMapper.selectList(Wrappers.<MemberPen>lambdaQuery()
                .eq(MemberPen::getFarmId, farmId)
                .eq(MemberPen::getMemberId, memberId)
                .orderByAsc(MemberPen::getPenId)));
    }

    /** 某栏舍的全部负责员工（t_member_pen，含已离开成员的历史行，调用方按在场成员过滤） */
    public List<MemberPen> pensOfFarm(Long farmId) {
        return TenantSkip.call(() -> memberPenMapper.selectList(Wrappers.<MemberPen>lambdaQuery()
                .eq(MemberPen::getFarmId, farmId)
                .orderByAsc(MemberPen::getPenId)));
    }
}
