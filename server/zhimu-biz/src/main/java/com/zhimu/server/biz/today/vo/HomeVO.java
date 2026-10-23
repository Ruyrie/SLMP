package com.zhimu.server.biz.today.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 首页聚合（API-T01）。owner_metrics、ai_hint 只给所有者和场长；staff_metrics、my_pens 只给员工；
 * 养殖场待审核或已驳回时只返回基本信息与 farm_status。
 */
@Getter
@Setter
public class HomeVO {

    private String serverTime;
    private String greeting;
    private String userName;
    private String avatarText;
    /** FARM_ROLE：OWNER / MANAGER / STAFF */
    private String role;
    private String roleText;
    private String farmStatus;
    private String farmStatusText;
    private FarmBriefVO farm;
    private Boolean hasPen;
    /** 全部养殖场未读 */
    private Integer unreadCount;
    private OwnerMetricsVO ownerMetrics;
    private StaffMetricsVO staffMetrics;
    private String aiHint;
    private List<MyPenVO> myPens;

    @JsonIgnore
    public boolean isFarmBlocked() {
        return "PENDING".equals(farmStatus) || "REJECTED".equals(farmStatus);
    }
}
