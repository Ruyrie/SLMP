package com.zhimu.slmp.today.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FarmBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 首页聚合（API-T01 data）。服务端按角色裁剪：owner_metrics、ai_hint 只给所有者和场长，staff_metrics、my_pens 只给员工；
 * 养殖场待审核或已驳回时只返回基本信息与 farm_status。production_alerts、pen_occupancy 只给 Web，App 不定义。
 */
public class HomeVO {

    /** 北京时间，yyyy-MM-dd HH:mm:ss；“今天”以此为准 */
    @Nullable
    private String serverTime;
    @Nullable
    private String greeting;
    @Nullable
    private String userName;
    @Nullable
    private String avatarText;
    /** FARM_ROLE：OWNER / MANAGER / STAFF */
    @Nullable
    private String role;
    /** “你是本场所有者”“东山猪场员工” */
    @Nullable
    private String roleText;
    /** PENDING / REJECTED / NORMAL / DISABLED / FROZEN */
    @Nullable
    private String farmStatus;
    @Nullable
    private String farmStatusText;
    @Nullable
    private FarmBriefVO farm;
    private boolean hasPen;
    /** 全部养殖场未读 */
    private int unreadCount;
    @Nullable
    private OwnerMetricsVO ownerMetrics;
    @Nullable
    private StaffMetricsVO staffMetrics;
    @Nullable
    private String aiHint;
    @Nullable
    private List<MyPenVO> myPens;

    @Nullable
    public String getServerTime() {
        return serverTime;
    }

    @Nullable
    public String getGreeting() {
        return greeting;
    }

    @Nullable
    public String getUserName() {
        return userName;
    }

    @Nullable
    public String getAvatarText() {
        return avatarText;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getFarmStatus() {
        return farmStatus;
    }

    @Nullable
    public String getFarmStatusText() {
        return farmStatusText;
    }

    @Nullable
    public FarmBriefVO getFarm() {
        return farm;
    }

    public boolean isHasPen() {
        return hasPen;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    @Nullable
    public OwnerMetricsVO getOwnerMetrics() {
        return ownerMetrics;
    }

    @Nullable
    public StaffMetricsVO getStaffMetrics() {
        return staffMetrics;
    }

    @Nullable
    public String getAiHint() {
        return aiHint;
    }

    @NonNull
    public List<MyPenVO> getMyPens() {
        return myPens == null ? Collections.emptyList() : myPens;
    }

    /** 养殖场待审核或已驳回（只返回基本信息） */
    public boolean isFarmBlocked() {
        return "PENDING".equals(farmStatus) || "REJECTED".equals(farmStatus);
    }
}
