package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 菜单分组：平台概览、审核中心、内容运营、基础数据、用户与系统。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuGroupVO {
    private String groupCode;
    private String groupName;
    @Builder.Default
    private List<MenuPageVO> pages = new ArrayList<>();
}
