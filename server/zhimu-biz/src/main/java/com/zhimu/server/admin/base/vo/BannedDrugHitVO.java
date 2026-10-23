package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

/** 禁用药命中提示（U11 data.banned_hit；模块08/09 的 banned_hint 复用同一文案口径）。 */
@Getter
@Setter
public class BannedDrugHitVO {

    private Long bannedDrugId;
    private String name;
    private String message;

    public static BannedDrugHitVO of(Long bannedDrugId, String name, String message) {
        BannedDrugHitVO vo = new BannedDrugHitVO();
        vo.setBannedDrugId(bannedDrugId);
        vo.setName(name);
        vo.setMessage(message);
        return vo;
    }
}
