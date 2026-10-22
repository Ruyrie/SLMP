package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 成员列表项（API-F16）。all_pens 为 true（所有者、场长）时显示“全部栏舍”；pens 只对员工。
 */
public class MemberItemVO {

    private long memberId;
    private long userId;
    @Nullable
    private String name;
    @Nullable
    private String nameInitial;
    @Nullable
    private String phoneMasked;
    @Nullable
    private String roleText;
    @Nullable
    private String joinTypeText;
    @Nullable
    private String joinedAt;
    /** OWNER / MANAGER / STAFF */
    @Nullable
    private String role;
    /** CREATE / INVITE */
    @Nullable
    private String joinType;
    private boolean isMe;
    private boolean allPens;
    private boolean canAdjustPens;
    @Nullable
    private List<PenBriefVO> pens;
    private int version;

    public long getMemberId() {
        return memberId;
    }

    public long getUserId() {
        return userId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getNameInitial() {
        return nameInitial;
    }

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getJoinTypeText() {
        return joinTypeText;
    }

    @Nullable
    public String getJoinedAt() {
        return joinedAt;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    @Nullable
    public String getJoinType() {
        return joinType;
    }

    public boolean isMe() {
        return isMe;
    }

    public boolean isAllPens() {
        return allPens;
    }

    public boolean isCanAdjustPens() {
        return canAdjustPens;
    }

    @NonNull
    public List<PenBriefVO> getPens() {
        return pens == null ? Collections.emptyList() : pens;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberItemVO)) {
            return false;
        }
        MemberItemVO that = (MemberItemVO) o;
        return memberId == that.memberId && version == that.version && Objects.equals(role, that.role)
                && Objects.equals(name, that.name) && getPens().size() == that.getPens().size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, version, role);
    }
}
