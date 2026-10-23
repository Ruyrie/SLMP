package com.zhimu.server.admin.system.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/** ADM-S05 当前管理员、权限、菜单、角标：在登录响应基础上加四个待审核数与未读消息数。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AdminMeVO extends AdminLoginVO {

    /** farm_audit、farm_change、change_request、account_recovery 待审核数（管理端-01 的四个 countPending） */
    private Map<String, Integer> badges = new LinkedHashMap<>();

    private Integer unreadMessageCount;

    /** 由登录 VO 复制公共字段，再补充角标；避免为继承层次引入两套 Builder */
    public static AdminMeVO from(AdminLoginVO login) {
        AdminMeVO me = new AdminMeVO();
        me.setAdmin(login.getAdmin());
        me.setMustChangePassword(login.getMustChangePassword());
        me.setTempPasswordExpireAt(login.getTempPasswordExpireAt());
        me.setIdleMinutes(login.getIdleMinutes());
        me.setPermissions(login.getPermissions());
        me.setMenus(login.getMenus());
        return me;
    }
}
