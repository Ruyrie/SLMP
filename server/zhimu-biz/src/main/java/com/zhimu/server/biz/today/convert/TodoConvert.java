package com.zhimu.server.biz.today.convert;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoRemind;
import com.zhimu.server.biz.today.mapper.TodoRemindMapper;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.vo.RelatedBatchVO;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.common.enums.TodoCategory;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.enums.TodoType;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待办 Entity → VO：展示状态、逾期天数、副文字、尾部标签、接收人、提醒与去向全部在这里生成，
 * 避免 App/Web 各自拼接导致员工裁剪不一致。员工视图不输出 assignee_text、amount。
 */
@Component
@RequiredArgsConstructor
public class TodoConvert {

    private static final DecimalFormat AMOUNT = new DecimalFormat("#,##0.##");

    /** 非今天的待办在标题前显示日期，避免历史逾期和今日同标题任务看起来重复。 */
    private static String displayTitle(Todo todo, LocalDate today) {
        String title = todo.getTitle();
        if (title == null || todo.getDueDate() == null || todo.getDueDate().equals(today)) {
            return title;
        }
        return TodayText.monthDay(todo.getDueDate()) + " · " + title;
    }

    private final TodoRemindMapper remindMapper;
    private final TodoLinks links;
    private final AccountQueryService accountQuery;

    /** 列表项；farmName 由调用方按养殖场批量取，员工视图不输出负责人与金额 */
    public TodoItemVO toItem(Todo todo, String farmName, boolean staff, LocalDate today, LocalDateTime now) {
        TodoItemVO vo = new TodoItemVO();
        vo.setTodoId(todo.getId());
        vo.setFarmId(todo.getFarmId());
        vo.setFarmName(farmName);
        vo.setTodoType(todo.getTodoType());
        vo.setTodoTypeText(TodayText.todoTypeText(todo));
        vo.setCategory(todo.getCategory());
        vo.setCategoryText(TodayText.categoryText(todo));
        TodoStatus status = TodayText.displayStatus(todo, now);
        vo.setDisplayStatus(status.name());
        vo.setDisplayStatusText(status.getText());
        vo.setTitle(displayTitle(todo, today));
        vo.setSubText(subText(todo, staff, today));
        vo.setTailText(TodayText.tailText(todo, today, now));
        vo.setDueDate(todo.getDueDate() == null ? null : todo.getDueDate().toString());
        vo.setDueTime(TodayText.hm(todo.getDueTime()));
        vo.setTimeType(todo.getTimeType());
        vo.setTimeLabel(TodayText.timeLabel(todo));
        vo.setOverdueDays(TodayText.overdueDays(todo, today));
        vo.setPenId(todo.getPenId());
        vo.setPenName(todo.getPenName());
        vo.setBatchId(todo.getBatchId());
        vo.setBatchNo(todo.getBatchNo());
        if (!staff) {
            vo.setAssigneeText(todo.getAssigneeName());
            if (todo.getAmount() != null) {
                vo.setAmount(todo.getAmount().setScale(2, RoundingMode.HALF_UP));
            }
        }
        vo.setCompletedText(completedText(todo, staff));
        vo.setOverdueDone(Boolean.TRUE.equals(todo.getOverdueDone()) ? Boolean.TRUE : null);
        vo.setIgnoreReason(todo.getIgnoreReason());
        vo.setIgnoreText(ignoreText(todo));
        vo.setActionText("去登记");
        vo.setListLink(links.listLink(todo, farmName));
        vo.setRegisterLink(links.registerLink(todo, farmName));
        vo.setVersion(todo.getVersion() == null ? 0 : todo.getVersion());
        return vo;
    }

    /** 详情：在列表项基础上补 T04 的专属行（副标题、来源、接收人、提醒、说明、关联批次） */
    public TodoDetailVO toDetail(Todo todo, String farmName, boolean staff, LocalDate today, LocalDateTime now) {
        TodoDetailVO vo = new TodoDetailVO();
        BeanUtils.copyProperties(toItem(todo, farmName, staff, today, now), vo);

        TodoStatus status = TodayText.displayStatus(todo, now);
        vo.setSubtitle(subtitle(todo, staff, today, now));
        vo.setSourceModuleName(TodayText.sourceModuleName(todo.getTodoType()));
        vo.setDueText(TodayText.dateWithWeekday(todo.getDueDate()));
        vo.setDueHint("WITHDRAWAL_END".equals(todo.getTodoType()) ? "可出栏日期变化时随之调整" : null);
        vo.setStatusNote(todo.getStatusNote());
        vo.setDetailLabel(todo.getDetailLabel());
        vo.setDetailValue(todo.getDetailValue());
        vo.setRemindCount(todo.getRemindCount() == null ? 0 : todo.getRemindCount());
        vo.setRemindText(remindText(todo));
        vo.setCompleteCondition(TodayText.completeCondition(todo.getTodoType()));

        fillReceivers(vo, todo, staff);
        vo.setNoticeText(TodayText.noticeText(todo, staff, now));
        vo.setMarkDoneTip(TodayText.markDoneTip(todo.getTodoType()));
        vo.setCompleteWay(todo.getCompleteWay());
        vo.setCompleteWayText(completeWayText(todo.getCompleteWay(), todo.getOverdueDone(), todo.getTodoType()));
        vo.setCompletedAt(todo.getCompletedAt() == null ? null : TodayText.dateTime(todo.getCompletedAt()));
        vo.setCompletedBy(userBrief(todo.getCompletedBy(), staff));
        vo.setIgnoredAt(todo.getIgnoredAt() == null ? null : TodayText.dateTime(todo.getIgnoredAt()));
        vo.setIgnoredBy(userBrief(todo.getIgnoredBy(), staff));
        vo.setRelatedBatch(relatedBatch(todo, farmName));

        boolean manager = !staff;
        boolean pending = status == TodoStatus.PENDING || status == TodoStatus.OVERDUE;
        vo.setCanRegister(pending);
        vo.setCanComplete(manager && pending && TodoCategory.WORK.name().equals(todo.getCategory()));
        vo.setCanIgnore(manager && pending);
        return vo;
    }

    private void fillReceivers(TodoDetailVO vo, Todo todo, boolean staff) {
        if (staff) {
            vo.setReceiversText(accountName(todo.getAssigneeId()) == null ? "本人" : accountName(todo.getAssigneeId()));
            return;
        }
        String assignee = todo.getAssigneeName();
        if (assignee != null && !assignee.isBlank()) {
            vo.setReceiversText(assignee + "（员工）");
            return;
        }
        vo.setReceiversText("所有者和场长");
        if ("PEN_STAFF".equals(todo.getReceiverScope())) {
            vo.setReceiversHint("栏舍没有负责员工，由所有者和场长执行");
        }
    }

    private RelatedBatchVO relatedBatch(Todo todo, String farmName) {
        Long batchId = todo.getRelatedBatchId() != null ? todo.getRelatedBatchId() : todo.getBatchId();
        if (batchId == null) {
            return null;
        }
        RelatedBatchVO batch = new RelatedBatchVO();
        batch.setBatchId(batchId);
        batch.setBatchNo(todo.getRelatedBatchNo() != null ? todo.getRelatedBatchNo() : todo.getBatchNo());
        batch.setPenName(todo.getPenName());
        batch.setDesc(todo.getRelatedBatchDesc() != null ? todo.getRelatedBatchDesc() : todo.getContentText());
        batch.setTagText(todo.getRelatedBatchTag() != null ? todo.getRelatedBatchTag()
                : ("WITHDRAWAL_END".equals(todo.getTodoType()) ? "休药中" : null));
        batch.setLink(links.batchLink(batchId, todo.getFarmId(), farmName));
        return batch;
    }

    private UserBriefVO userBrief(Long userId, boolean staff) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQuery.getBrief(userId);
        UserBriefVO vo = new UserBriefVO();
        vo.setUserId(userId);
        vo.setName(brief == null ? (userId == 0 ? "系统" : null) : brief.name());
        return vo;
    }

    private String accountName(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQuery.getBrief(userId);
        return brief == null ? null : brief.name();
    }

    private String subtitle(Todo todo, boolean staff, LocalDate today, LocalDateTime now) {
        TodoStatus status = TodayText.displayStatus(todo, now);
        if (status == TodoStatus.DONE && todo.getCompletedAt() != null) {
            String name = accountName(todo.getCompletedBy());
            String action = "MANUAL".equals(todo.getCompleteWay()) ? "手动标记完成" : "登记";
            return TodayText.monthDay(todo.getCompletedAt().toLocalDate()) + " "
                    + TodayText.hm(todo.getCompletedAt()) + " 由" + (name == null ? "系统" : name) + action;
        }
        if (status == TodoStatus.IGNORED) {
            String name = accountName(todo.getIgnoredBy());
            return TodayText.monthDay(todo.getDueDate()) + " 已由" + (name == null ? "系统" : name) + "忽略";
        }
        String base = "计划 " + TodayText.monthDay(todo.getDueDate());
        if (status == TodoStatus.OVERDUE) {
            Integer days = TodayText.overdueDays(todo, today);
            return base + " · 已逾期" + (days == null || days <= 0 ? "（今天已逾期）" : " " + days + " 天");
        }
        long days = todo.getDueDate() == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(today, todo.getDueDate());
        if (days > 0) {
            return base + " · 还有 " + days + " 天";
        }
        return base + " · 今天到期";
    }

    /** 2.7.6 副文字：员工不写负责人与金额 */
    private String subText(Todo todo, boolean staff, LocalDate today) {
        String type = todo.getTodoType() == null ? "" : todo.getTodoType();
        if (staff) {
            String base = switch (type) {
                case "FEEDING" -> todo.getContentText();
                case "DISINFECTION" -> todo.getContentText();
                case "IMMUNIZATION" -> (todo.getStatusNote() == null ? "" : todo.getStatusNote() + " · ")
                        + "计划 " + TodayText.monthDay(todo.getDueDate());
                case "INSPECTION" -> todo.getDueTime() == null ? "全天" : TodayText.hm(todo.getDueTime()) + " 截止";
                case "WEIGHING" -> "计划 " + TodayText.monthDay(todo.getDueDate());
                default -> todo.getContentText();
            };
            return blankToNull(base);
        }
        return switch (type) {
            case "FEEDING" -> "投喂时间 " + TodayText.hm(todo.getDueTime())
                    + (todo.getAssigneeName() == null ? "" : " · " + todo.getAssigneeName());
            case "DISINFECTION" -> "计划 " + TodayText.monthDay(todo.getDueDate())
                    + (todo.getAssigneeName() == null ? " · 无负责员工" : " · " + todo.getAssigneeName());
            case "IMMUNIZATION" -> (todo.getStatusNote() == null ? "" : todo.getStatusNote() + " · ")
                    + "计划 " + TodayText.monthDay(todo.getDueDate())
                    + (todo.getAssigneeName() == null ? "" : " · " + todo.getAssigneeName());
            case "INSPECTION" -> (todo.getDueTime() == null ? "全天" : TodayText.hm(todo.getDueTime()) + " 截止")
                    + (todo.getAssigneeName() == null ? "" : " · " + todo.getAssigneeName());
            case "WITHDRAWAL_END" -> (todo.getPenName() == null ? "" : todo.getPenName() + " · ")
                    + TodayText.monthDay(todo.getDueDate()) + "可出栏";
            case "RECEIVABLE" -> "约定 " + TodayText.monthDay(todo.getDueDate())
                    + (todo.getAmount() == null ? "" : " · 未收 " + AMOUNT.format(todo.getAmount()) + " 元");
            case "FARROWING", "HATCHING", "FEED_LOW_STOCK", "DRUG_LOW_STOCK", "DRUG_NEAR_EXPIRY",
                 "DRUG_EXPIRED" -> todo.getContentText();
            default -> todo.getContentText();
        };
    }

    private String completedText(Todo todo, boolean staff) {
        if (!TodoStatus.DONE.name().equals(todo.getStatus()) || todo.getCompletedAt() == null) {
            return null;
        }
        String name = staff ? "" : accountName(todo.getCompletedBy());
        String prefix = name == null || name.isBlank() ? "" : name + " ";
        String action = "MANUAL".equals(todo.getCompleteWay()) ? "手动标记完成" : "登记";
        return prefix + TodayText.hm(todo.getCompletedAt()) + " " + action;
    }

    private String ignoreText(Todo todo) {
        if (!TodoStatus.IGNORED.name().equals(todo.getStatus())) {
            return null;
        }
        String name = accountName(todo.getIgnoredBy());
        if (name == null) {
            return "系统自动忽略";
        }
        return name + " " + TodayText.hm(todo.getIgnoredAt()) + " 忽略";
    }

    private String completeWayText(String way, Boolean overdueDone, String todoType) {
        if (way == null) {
            return null;
        }
        return switch (way) {
            case "REGISTER" -> Boolean.TRUE.equals(overdueDone) ? "逾期登记" : registerWayText(todoType);
            case "MANUAL" -> "手动标记，未生成记录";
            case "AUTO" -> "系统自动完成";
            default -> way;
        };
    }

    /** 登记类完成方式写明登记了什么，例如“登记投喂记录”（与 App 详情页文案一致） */
    private static String registerWayText(String todoType) {
        if (todoType == null) {
            return "登记记录";
        }
        return switch (todoType) {
            case "FEEDING" -> "登记投喂记录";
            case "INSPECTION" -> "登记巡检记录";
            case "IMMUNIZATION" -> "登记接种记录";
            case "DISINFECTION" -> "登记消毒记录";
            case "WEIGHING" -> "登记称重记录";
            case "FARROWING" -> "登记产仔记录";
            case "HATCHING" -> "登记出雏记录";
            case "WITHDRAWAL_END" -> "登记出栏记录";
            case "RECEIVABLE" -> "登记收款记录";
            case "ABNORMAL_REPORT" -> "登记处理结果";
            default -> "登记记录";
        };
    }

    /** 提醒说明由提醒计划拼出：10月17日、10月18日各一次，逾期后又提醒一次 */
    private String remindText(Todo todo) {
        List<TodoRemind> plans = remindMapper.selectList(Wrappers.<TodoRemind>lambdaQuery()
                .eq(TodoRemind::getTodoId, todo.getId())
                .orderByAsc(TodoRemind::getRemindAt));
        if (plans.isEmpty()) {
            return null;
        }
        List<String> sent = new ArrayList<>();
        boolean overdue = false;
        for (TodoRemind plan : plans) {
            if ("SENT".equals(plan.getStatus()) && plan.getRemindAt() != null) {
                sent.add(TodayText.monthDay(plan.getRemindAt().toLocalDate()));
                overdue = overdue || "OVERDUE".equals(plan.getRemindStage());
            }
        }
        if (sent.isEmpty()) {
            return null;
        }
        List<String> distinct = sent.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        String text = String.join("、", distinct) + "各一次";
        return overdue ? text + "，逾期后又提醒一次" : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 金额统一两位小数输出 */
    public static BigDecimal amount(Todo todo) {
        return todo.getAmount() == null ? null : todo.getAmount().setScale(2, RoundingMode.HALF_UP);
    }

    /** 供其他转换共用的来源名称（AI 消息的详情等） */
    public static String todoTypeText(String todoType) {
        TodoType type = com.zhimu.server.common.enums.CodeEnum.of(TodoType.class, todoType);
        return type == null ? todoType : type.getText();
    }

    /** 供首页等使用的待办标题（无实际待办时返回 null） */
    public static String titleOf(TodoSource.TodoDraft draft) {
        return draft == null ? null : draft.getTitle();
    }
}
