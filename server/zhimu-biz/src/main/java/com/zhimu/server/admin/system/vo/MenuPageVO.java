package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单页面（按有 VIEW 权限的页面生成；角标键由页面决定，例如 WA-R01 → farm_audit）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuPageVO {
    private String pageCode;
    private String pageName;
    private String badgeKey;
}
