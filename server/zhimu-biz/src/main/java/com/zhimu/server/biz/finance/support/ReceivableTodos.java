package com.zhimu.server.biz.finance.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.biz.today.api.TodoReminderService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 出栏单未收款 → 待收款待办（todo_type = RECEIVABLE，category = BUSINESS，所有者和场长可见）与收款提醒。
 * <p>
 * 未收款 / 部分收款（含赊账）时保持一条未完成待办，金额 = 未收金额、截止日 = 约定收款日（未填按出栏后第 7 天）；
 * 收齐后自动完成，作废后自动忽略，审核改价导致重新欠款时恢复为未完成。
 * 提醒规则见 {@link FinanceConst#reminderRuleText()}：约定收款日当天提醒，逾期后从第 3 天起每 3 天提醒一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceivableTodos {

    public static final String TODO_TYPE = "RECEIVABLE";
    private static final String BIZ_TYPE = "SALE_ORDER";
    private static final DateTimeFormatter CYCLE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SaleOrderMapper orderMapper;
    private final TodoMapper todoMapper;
    private final BatchQueryService batchQueryService;
    // 延迟获取，避免与待办服务、消息服务形成构造依赖环
    private final ObjectProvider<TodoSourceWriteService> writerProvider;
    private final ObjectProvider<TodoReminderService> reminderProvider;
    private final ObjectProvider<MessageService> messageProvider;

    public static String dedupKey(Long orderId) {
        return BIZ_TYPE + ":" + orderId;
    }

    /** 未收款或部分收款且还有未收金额 */
    public static boolean isOpen(SaleOrder order) {
        return order != null
                && (FinanceConst.PAY_UNPAID.equals(order.getPaymentStatus())
                || FinanceConst.PAY_PARTIAL.equals(order.getPaymentStatus()))
                && FinanceSupport.nz(order.getUnpaidAmount()).signum() > 0;
    }

    /** 按出栏单当前收款状态写入 / 刷新 / 关闭待收款待办 */
    public void sync(SaleOrder order) {
        if (order == null || order.getId() == null || order.getFarmId() == null) {
            return;
        }
        TodoSourceWriteService writer = writerProvider.getObject();
        String key = dedupKey(order.getId());
        if (isOpen(order)) {
            // 审核改价后重新欠款：已登记完成的待办恢复为未完成
            writer.reopenFromSource(order.getFarmId(), TODO_TYPE, key, null);
            writer.upsertFromSource(order.getFarmId(), List.of(draft(order, BeijingTime.today())));
        } else if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            writer.ignoreFromSource(order.getFarmId(), TODO_TYPE, key, "出栏单已作废");
        } else {
            writer.completeFromSource(order.getFarmId(), TODO_TYPE, key, BIZ_TYPE, order.getId(),
                    order.getUpdatedBy() == null ? 0L : order.getUpdatedBy());
        }
    }

    /** 全部养殖场对账（启动与每天凌晨，可重复执行）：补齐未收款出栏单的待办，关闭已收齐 / 已作废出栏单的待办 */
    public int reconcileAll() {
        List<SaleOrder> open = openOrders();
        int count = 0;
        for (SaleOrder order : open) {
            try {
                sync(order);
                count++;
            } catch (RuntimeException e) {
                log.warn("待收款待办同步失败：saleOrderId={}", order.getId(), e);
            }
        }
        List<Todo> pending = TenantSkip.call(() -> todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getTodoType, TODO_TYPE).eq(Todo::getStatus, "PENDING")));
        for (Todo todo : pending) {
            SaleOrder order = todo.getBizId() == null ? null
                    : TenantSkip.call(() -> orderMapper.selectById(todo.getBizId()));
            if (isOpen(order)) {
                continue;
            }
            try {
                if (order == null) {
                    writerProvider.getObject().ignoreFromSource(todo.getFarmId(), TODO_TYPE, todo.getDedupKey(),
                            "出栏单已删除");
                } else {
                    sync(order);
                }
            } catch (RuntimeException e) {
                log.warn("待收款待办关闭失败：todoId={}", todo.getId(), e);
            }
        }
        return count;
    }

    /** 按提醒规则给所有者和场长发收款提醒；同一出栏单同一天只发一次 */
    public int remind(LocalDate today) {
        int sent = 0;
        for (SaleOrder order : openOrders()) {
            String stage = stageOf(order.getEffectiveDueDate(), today);
            if (stage == null) {
                continue;
            }
            try {
                if (remindOne(order, stage, today)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("收款提醒发送失败：saleOrderId={}", order.getId(), e);
            }
        }
        return sent;
    }

    /** DUE：约定收款日当天；OVERDUE：逾期第 3 天；REPEAT：之后每 3 天；其余日期不提醒 */
    static String stageOf(LocalDate due, LocalDate today) {
        if (due == null || today == null) {
            return null;
        }
        long overdue = ChronoUnit.DAYS.between(due, today);
        if (overdue == 0) {
            return "DUE";
        }
        if (overdue >= 3 && (overdue - 3) % 3 == 0) {
            return overdue == 3 ? "OVERDUE" : "REPEAT";
        }
        return null;
    }

    private boolean remindOne(SaleOrder order, String stage, LocalDate today) {
        sync(order);
        Set<Long> receivers = new LinkedHashSet<>(TenantSkip.call(
                () -> orderMapper.selectManagerUserIds(order.getFarmId())));
        receivers.removeIf(Objects::isNull);
        if (receivers.isEmpty()) {
            return false;
        }
        Long todoId = reminderProvider.getObject().claim(order.getFarmId(), TODO_TYPE, dedupKey(order.getId()),
                stage, today.format(CYCLE), "MANAGERS", receivers.size());
        if (todoId == null) {
            return false;
        }
        long overdue = ChronoUnit.DAYS.between(order.getEffectiveDueDate(), today);
        String name = displayNo(order);
        String title = overdue <= 0 ? name + " 今天到期收款" : name + " 收款已逾期 " + overdue + " 天";
        messageProvider.getObject().send(MessageCommand.builder()
                .msgType(MsgType.TODO_REMIND)
                .farmId(order.getFarmId())
                .receiverUserIds(receivers)
                .title(title)
                .summary(summary(order))
                .bizType(BizType.SALE_ORDER)
                .bizId(order.getId())
                .mergeKey("RECEIVABLE:" + order.getId() + ":" + today.format(CYCLE))
                .route("E07")
                .extra("sale_order_id", order.getId())
                .extra("todo_id", todoId)
                .build());
        return true;
    }

    private List<SaleOrder> openOrders() {
        return TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .in(SaleOrder::getPaymentStatus, FinanceConst.PAY_UNPAID, FinanceConst.PAY_PARTIAL)
                .gt(SaleOrder::getUnpaidAmount, BigDecimal.ZERO)
                .orderByAsc(SaleOrder::getId)));
    }

    private TodoSource.TodoDraft draft(SaleOrder order, LocalDate today) {
        LocalDate due = order.getEffectiveDueDate() != null ? order.getEffectiveDueDate()
                : order.getSaleDate() == null ? today : order.getSaleDate().plusDays(7);
        String batchNo = batchNo(order.getBatchId());
        String name = batchNo != null ? batchNo : order.getSaleNo();
        boolean overdue = today.isAfter(due);
        String detail = (order.getSaleNo() == null ? "" : order.getSaleNo() + " · ")
                + (order.getBuyerName() == null ? "" : order.getBuyerName());
        return TodoSource.TodoDraft.builder()
                .todoType(TODO_TYPE)
                .category("BUSINESS")
                .penId(null)
                .batchId(order.getBatchId())
                .batchNo(batchNo)
                .bizType(BIZ_TYPE)
                .bizId(order.getId())
                .dedupKey(dedupKey(order.getId()))
                .title(cut((name == null ? "" : name + " ") + (overdue ? "收款已逾期" : "待收款"), 60))
                .contentText(cut(summary(order), 100))
                .detailLabel("出栏单")
                .detailValue(cut(detail, 100))
                .amount(FinanceSupport.scale2(FinanceSupport.nz(order.getUnpaidAmount())))
                .receiverScope("MANAGERS")
                .dueDate(due)
                .timeType("ALL_DAY")
                .overdueAt(due.plusDays(1).atStartOfDay())
                .build();
    }

    private String summary(SaleOrder order) {
        String buyer = order.getBuyerName() == null || order.getBuyerName().isBlank() ? "" : order.getBuyerName() + " · ";
        return buyer + "未收 " + new DecimalFormat("#,##0.##").format(FinanceSupport.nz(order.getUnpaidAmount())) + " 元";
    }

    private String displayNo(SaleOrder order) {
        String batchNo = batchNo(order.getBatchId());
        return batchNo != null ? batchNo : order.getSaleNo();
    }

    private String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId).batchNo();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cut(String value, int max) {
        if (value == null || value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }
}
