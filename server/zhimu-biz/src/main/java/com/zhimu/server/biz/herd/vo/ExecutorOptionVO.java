package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L25 实际执行人选项：本人排第一（display_name 带“（我）”），其后为负责该栏舍的员工。 */
@Data
public class ExecutorOptionVO {

    private Long userId;
    private String name;
    private String displayName;
    private String role;
    private Boolean isMe;
}
