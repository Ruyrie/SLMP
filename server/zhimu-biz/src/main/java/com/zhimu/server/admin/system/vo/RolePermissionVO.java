package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ADM-S28 角色权限表：头部角色与成员；rows 按功能块与页面列出查看 / 处理两个权限点。
 * locked = true 表示复选框锁定：超级管理员角色全部锁定，非超管角色中的超管专属权限点锁定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionVO {

    private RoleItemVO role;
    @Builder.Default
    private List<Member> members = new ArrayList<>();
    private Boolean editable;
    private Integer version;
    @Builder.Default
    private List<Row> rows = new ArrayList<>();
    @Builder.Default
    private List<String> defaultCodes = new ArrayList<>();

    /** 角色成员（含已停用，status 供前端置灰） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Member {
        private Long adminId;
        private String name;
        private String status;
        private String statusText;
    }

    /** 一个权限勾选项 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cell {
        private String permCode;
        private Boolean checked;
        private Boolean locked;
    }

    /** 一个页面：view 必有，handle 无处理动作时为 null（不输出） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private String moduleCode;
        private String moduleName;
        private String pageCode;
        private String pageName;
        private Cell view;
        private Cell handle;
        private String handleDesc;
    }
}
