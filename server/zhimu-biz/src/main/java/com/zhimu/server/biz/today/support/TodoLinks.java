package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.vo.LinkVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.TodoStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待办去向（2.7.6、2.8.1）：未完成用 list_link，已完成/已忽略进 T04；
 * need_switch 为目标养殖场与当前 X-Farm-Id 不同（客户端先调 API-F03）。
 */
@Component
public class TodoLinks {

    /** 列表点击去向 */
    public LinkVO listLink(Todo todo, String farmName) {
        TodoStatus status = TodayText.displayStatus(todo, LocalDateTime.now());
        if (status == TodoStatus.DONE || status == TodoStatus.IGNORED) {
            return link("T04", todo.getFarmId(), farmName, params(todo, false));
        }
        return link(listRoute(todo.getTodoType()), todo.getFarmId(), farmName, params(todo, true));
    }

    /** “去登记”去向 */
    public LinkVO registerLink(Todo todo, String farmName) {
        return link(registerRoute(todo.getTodoType()), todo.getFarmId(), farmName, params(todo, true));
    }

    public LinkVO batchLink(Long batchId, Long farmId, String farmName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("batch_id", String.valueOf(batchId));
        return link("L06", farmId, farmName, params);
    }

    public static LinkVO link(String route, Long farmId, String farmName, Map<String, String> params) {
        LinkVO vo = new LinkVO();
        vo.setRoute(route);
        vo.setFarmId(farmId);
        vo.setFarmName(farmName);
        Long current = FarmContext.farmId();
        vo.setNeedSwitch(farmId != null && current != null && !farmId.equals(current));
        vo.setParams(params == null ? Map.of() : params);
        return vo;
    }

    /** 待办类型对应的列表去向 */
    public static String listRoute(String todoType) {
        return switch (todoType == null ? "" : todoType) {
            case "FEEDING" -> "G02";
            case "INSPECTION" -> "I02";
            case "IMMUNIZATION" -> "D03";
            case "DISINFECTION" -> "D08";
            case "WEIGHING" -> "G05";
            case "FARROWING", "HATCHING" -> "B02";
            case "FEED_LOW_STOCK" -> "M01";
            case "DRUG_LOW_STOCK" -> "M05";
            case "DRUG_NEAR_EXPIRY", "DRUG_EXPIRED" -> "M06";
            case "WITHDRAWAL_END" -> "D05";
            case "RECEIVABLE" -> "E07";
            default -> "T04";
        };
    }

    /** 待办类型对应的“去登记”去向 */
    public static String registerRoute(String todoType) {
        return switch (todoType == null ? "" : todoType) {
            case "FEEDING" -> "G02";
            case "INSPECTION" -> "I02";
            case "IMMUNIZATION" -> "D03";
            case "DISINFECTION" -> "D08";
            case "WEIGHING" -> "G05";
            case "FARROWING" -> "B04";
            case "HATCHING" -> "B07";
            case "FEED_LOW_STOCK" -> "M03";
            case "DRUG_LOW_STOCK" -> "M08";
            case "DRUG_NEAR_EXPIRY", "DRUG_EXPIRED" -> "M07";
            case "WITHDRAWAL_END" -> "E06";
            case "RECEIVABLE" -> "E07";
            default -> "T04";
        };
    }

    private static Map<String, String> params(Todo todo, boolean withBiz) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("todo_id", String.valueOf(todo.getId()));
        if (todo.getPenId() != null) {
            params.put("pen_id", String.valueOf(todo.getPenId()));
        }
        if (todo.getBatchId() != null) {
            params.put("batch_id", String.valueOf(todo.getBatchId()));
        }
        if (withBiz && todo.getBizId() != null) {
            switch (todo.getTodoType() == null ? "" : todo.getTodoType()) {
                case "FEEDING", "DISINFECTION" -> params.put("task_id", String.valueOf(todo.getBizId()));
                case "IMMUNIZATION" -> params.put("plan_item_id", String.valueOf(todo.getBizId()));
                case "INSPECTION" -> params.put("record_id", String.valueOf(todo.getBizId()));
                case "WEIGHING" -> params.put("cycle_id", String.valueOf(todo.getBizId()));
                case "FEED_LOW_STOCK" -> params.put("feed_id", String.valueOf(todo.getBizId()));
                case "DRUG_LOW_STOCK", "DRUG_NEAR_EXPIRY", "DRUG_EXPIRED" ->
                        params.put("drug_id", String.valueOf(todo.getBizId()));
                case "RECEIVABLE" -> params.put("sale_order_id", String.valueOf(todo.getBizId()));
                default -> {
                }
            }
        }
        return params;
    }
}
