package com.zhimu.server.biz.farm;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模块02 提供框架的成员上下文读取（全仓库唯一 Bean）：
 * 从 t_farm_member + t_farm + t_member_pen 组装 FarmContext.FarmMember。
 * 非成员、已移出、养殖场已删除返回 null；状态优先级 FROZEN → DISABLED → PENDING → REJECTED → NORMAL。
 * 所有者、场长的 penIds 为空集合（表示全部栏舍），员工必须查全负责栏舍。
 */
@Component
@RequiredArgsConstructor
public class FarmMemberLoaderImpl implements FarmMemberLoader {

    private final FarmLookup lookup;

    @Override
    public FarmContext.FarmMember load(Long farmId, Long userId) {
        if (farmId == null || userId == null) {
            return null;
        }
        Farm farm = lookup.findFarm(farmId);
        if (farm == null) {
            return null;
        }
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            return null;
        }
        FarmRole role = FarmRole.valueOf(member.getRole());
        Set<Long> penIds = new LinkedHashSet<>();
        if (role == FarmRole.STAFF) {
            List<MemberPen> pens = lookup.memberPens(farmId, member.getId());
            for (MemberPen pen : pens) {
                penIds.add(pen.getPenId());
            }
        }
        return new FarmContext.FarmMember(farm.getId(), farm.getName(), userId, member.getId(), role,
                Set.copyOf(penIds), FarmStatuses.of(farm));
    }
}
