package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-S31 操作日志详情：描述字段、终端、变更对比、同一对象的其他记录与跳转信息。
 * 不可见的日志按不存在处理（40401）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogDetailVO {

    private Operator operator;
    private String opType;
    private String opTypeText;
    private String opAction;
    private String opActionText;
    private String result;
    private String resultText;
    private String targetName;
    private String reason;
    private String extraText;
    private String notifyText;
    private Client client;
    @Builder.Default
    private List<Change> changes = new ArrayList<>();
    @Builder.Default
    private List<Related> related = new ArrayList<>();
    private Link link;

    /** 操作人快照；用户名按 operator_id 查 t_admin 补齐（已删除/系统操作时为空） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operator {
        private Long adminId;
        private String name;
        private String username;
        private String role;
        private String roleText;
    }

    /** 终端信息：网页版 / 浏览器 / IP 属地 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Client {
        private String clientType;
        private String clientTypeText;
        private String browser;
        private String ipRegion;
    }

    /** 一项字段变化：before / after 为显示文本 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Change {
        private String fieldName;
        private String before;
        private String after;
    }

    /** 同一对象的其他日志（最多 10 条），可含用户端日志 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Related {
        private LocalDateTime time;
        private String desc;
        private String source;
        private String sourceText;
        private String statusText;
    }

    /** 跳转：page_code 为管理端页面编号，exists 表示业务对象是否仍可打开 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Link {
        private String pageCode;
        private String bizType;
        private Long bizId;
        private Boolean exists;
    }
}
