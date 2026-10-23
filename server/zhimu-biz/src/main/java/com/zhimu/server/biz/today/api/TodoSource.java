package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.context.FarmContext;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 待办来源扩展点：模块04–11 在各自事务内调用 {@link #generate} 或直接写 TODO 表后注册本 Bean，
 * 模块03 的列表、日历与首页即可看到该来源的待办；没有实现时只用种子/已有数据。
 * <p>
 * 约定与 2.8.1 节一致：同一 (farm_id, todo_type, dedup_key) 只一条；DONE / IGNORED 的行只更新展示字段。
 */
public interface TodoSource {

    /** 来源模块名，例如 herd、feeding、health、finance */
    String moduleName();

    /** 生成或更新待办；返回本次涉及的待办 ID（幂等失败可返回空列表） */
    List<Long> generate(TodoSourceContext context);

    /** 生成上下文：养殖场、业务日期与当前成员 */
    @Value
    @Builder
    class TodoSourceContext {
        Long farmId;
        String farmName;
        LocalDate date;
        FarmContext.FarmMember member;
    }

    /** 来源提交的一条待办 */
    @Value
    @Builder
    class TodoDraft {
        String todoType;
        String category;
        Long penId;
        Long batchId;
        String penName;
        String batchNo;
        String bizType;
        Long bizId;
        String dedupKey;
        String title;
        String contentText;
        String statusNote;
        String detailLabel;
        String detailValue;
        BigDecimal amount;
        String receiverScope;
        Long assigneeId;
        String assigneeName;
        LocalDate dueDate;
        String timeType;
        LocalTime dueTime;
        LocalDateTime overdueAt;
        @Singular
        Map<String, Object> extras;
    }
}
