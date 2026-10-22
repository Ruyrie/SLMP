package com.zhimu.slmp.herd.model.vo;

/**
 * API-L25 实际执行人选项：本人排第一（display_name 带“（我）”），其后为负责该栏舍的员工。
 */
public class ExecutorOptionVO {

    private long userId;
    private String name;
    private String displayName;
    private String role;
    private boolean isMe;

    public long getUserId() { return userId; }

    public String getName() { return name; }

    public String getDisplayName() { return displayName; }

    public String getRole() { return role; }

    public boolean isMe() { return isMe; }
}
