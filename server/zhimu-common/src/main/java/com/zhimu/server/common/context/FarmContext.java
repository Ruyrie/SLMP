package com.zhimu.server.common.context;

import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.util.Set;

/**
 * 当前养殖场上下文：由 FarmContextInterceptor 按 X-Farm-Id（模块02 管理类接口取路径中的 farmId）写入。
 * 供 Service 校验角色与栏舍范围、供多租户插件按 farm_id 隔离、供序列化按角色裁剪管理视图字段。
 */
public final class FarmContext {

    /**
     * @param farmId     当前养殖场 ID
     * @param farmName   养殖场名称（推送标题、提示文案用）
     * @param userId     当前用户 ID
     * @param memberId   t_farm_member.id
     * @param role       在该养殖场中的角色
     * @param penIds     员工负责的栏舍 ID；所有者、场长为空集合（表示全部栏舍）
     * @param farmStatus 派生状态 PENDING / REJECTED / NORMAL / DISABLED / FROZEN
     */
    public record FarmMember(Long farmId, String farmName, Long userId, Long memberId, FarmRole role,
                             Set<Long> penIds, FarmStatus farmStatus) {

        public boolean isOwner() {
            return role == FarmRole.OWNER;
        }

        public boolean isStaff() {
            return role == FarmRole.STAFF;
        }

        /** 所有者、场长可以看单价、金额等管理视图字段 */
        public boolean isManagerView() {
            return role == FarmRole.OWNER || role == FarmRole.MANAGER;
        }

        /** 所有者、场长可访问全部栏舍；员工只能访问负责栏舍 */
        public boolean canAccessPen(Long penId) {
            if (!isStaff()) {
                return true;
            }
            return penId != null && penIds != null && penIds.contains(penId);
        }
    }

    private static final ThreadLocal<FarmMember> HOLDER = new ThreadLocal<>();

    private FarmContext() {
    }

    public static FarmMember get() {
        return HOLDER.get();
    }

    public static void set(FarmMember member) {
        HOLDER.set(member);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前养殖场 ID；没有养殖场上下文时为 null */
    public static Long farmId() {
        FarmMember member = HOLDER.get();
        return member == null ? null : member.farmId();
    }

    /** 当前养殖场成员信息；没有时抛 40302 */
    public static FarmMember require() {
        FarmMember member = HOLDER.get();
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FARM_MEMBER);
        }
        return member;
    }

    public static Long requireFarmId() {
        return require().farmId();
    }

    /** 员工视图：没有上下文时按非员工处理（例如管理端、定时任务） */
    public static boolean isStaff() {
        FarmMember member = HOLDER.get();
        return member != null && member.isStaff();
    }

    /** 员工越出负责栏舍时抛 40304 */
    public static void checkPen(Long penId) {
        if (!require().canAccessPen(penId)) {
            throw new BizException(ErrorCode.PEN_FORBIDDEN);
        }
    }
}
