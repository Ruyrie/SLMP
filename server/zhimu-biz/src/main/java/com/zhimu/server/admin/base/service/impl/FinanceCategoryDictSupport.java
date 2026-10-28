package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.event.BaseDataChangedEvent;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.query.IdCountRow;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.request.ItemOrderRequest;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * WA-D05 收支一级分类（t_finance_category，模块10 记账用）：新增、修改、启停、排序。
 * 编码不可改；管理员新增的分类编码为 EXP_/INC_ 加序号。兜底“其他”和自动记账分类不能停用。
 * 写操作由 {@link DictAdminServiceImpl} 的事务方法调用。
 */
@Component
@RequiredArgsConstructor
class FinanceCategoryDictSupport {

    private static final int NAME_MAX = 10;

    private final FinanceCategoryMapper mapper;
    private final BaseAdminReadMapper readMapper;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    /** 两类收支分类各自的项数（含停用） */
    Map<String, Integer> counts() {
        Map<String, Integer> counts = new HashMap<>();
        for (FinanceCategory category : mapper.selectList(null)) {
            String type = "EXPENSE".equals(category.getDirection()) ? DictTypes.EXPENSE_CATEGORY
                    : DictTypes.INCOME_CATEGORY;
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    List<DictItemVO> items(String type) {
        List<FinanceCategory> list = list(type);
        Map<Long, Integer> usage = toMap(readMapper.financeRecordCounts());
        Map<Long, Integer> subcategories = DictTypes.EXPENSE_CATEGORY.equals(type)
                ? toMap(readMapper.expenseSubcategoryCounts()) : Map.of();
        List<DictItemVO> result = new ArrayList<>();
        for (FinanceCategory category : list) {
            result.add(toVO(category, usage.getOrDefault(category.getId(), 0),
                    DictTypes.EXPENSE_CATEGORY.equals(type) ? subcategories.getOrDefault(category.getId(), 0) : null));
        }
        return result;
    }

    DictItemVO create(String type, DictItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String direction = DictTypes.financeDirection(type);
        String name = validateName(request.getName());
        List<FinanceCategory> existing = list(type);
        requireNameUnique(existing, name, null);
        FinanceCategory category = new FinanceCategory();
        category.setDirection(direction);
        category.setCode(nextCode(direction));
        category.setName(name);
        category.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim() : "category");
        category.setSortNo(request.getSortNo() == null ? nextSortNo(existing) : request.getSortNo());
        category.setStatus("ENABLED");
        category.setAutoSource(null);
        category.setMarkOnly(false);
        category.setIsFallback(false);
        category.setVersion(0);
        category.setUpdatedBy(adminId);
        mapper.insert(category);
        DictItemVO vo = toVO(category, 0, DictTypes.EXPENSE_CATEGORY.equals(type) ? 0 : null);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("CREATE").bizType("FINANCE_CATEGORY").bizId(category.getId())
                .targetName(DictTypes.displayName(type) + " · " + name).searchText(name)
                .after(vo).build());
        published(type);
        return vo;
    }

    DictItemVO update(String type, Long id, DictItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        FinanceCategory category = require(type, id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), category.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String name = validateName(request.getName());
        requireNameUnique(list(type), name, id);
        DictItemVO before = toVO(category, null, null);
        LocalDateTime now = BeijingTime.now();
        int rows = mapper.update(null, Wrappers.<FinanceCategory>lambdaUpdate()
                .eq(FinanceCategory::getId, id)
                .eq(FinanceCategory::getVersion, category.getVersion())
                .set(FinanceCategory::getName, name)
                .set(FinanceCategory::getIcon, StringUtils.hasText(request.getIcon())
                        ? request.getIcon().trim() : category.getIcon())
                .set(FinanceCategory::getSortNo, request.getSortNo() == null
                        ? category.getSortNo() : request.getSortNo())
                .set(FinanceCategory::getUpdatedBy, adminId)
                .set(FinanceCategory::getUpdatedAt, now)
                .set(FinanceCategory::getVersion, category.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        DictItemVO vo = toVO(mapper.selectById(id), null, null);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("UPDATE").bizType("FINANCE_CATEGORY").bizId(id)
                .targetName(DictTypes.displayName(type) + " · " + name).searchText(name)
                .before(before).after(vo).build());
        published(type);
        return vo;
    }

    void order(String type, ItemOrderRequest request) {
        AdminContext.requireAdminId();
        List<Long> ids = request.getItemIds();
        Map<Long, FinanceCategory> byId = new HashMap<>();
        for (FinanceCategory category : list(type)) {
            byId.put(category.getId(), category);
        }
        if (ids == null || ids.isEmpty() || !byId.keySet().containsAll(ids)) {
            throw BaseErrors.notFound();
        }
        int sort = 0;
        for (Long id : ids) {
            mapper.update(null, Wrappers.<FinanceCategory>lambdaUpdate()
                    .eq(FinanceCategory::getId, id)
                    .set(FinanceCategory::getSortNo, ++sort));
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction("SORT").bizType("FINANCE_CATEGORY")
                .targetName(DictTypes.displayName(type)).searchText(type)
                .after(Map.of("item_ids", ids)).build());
        published(type);
    }

    DictItemVO changeStatus(String type, Long id, DictStatusRequest request) {
        Long adminId = AdminContext.requireAdminId();
        FinanceCategory category = require(type, id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), category.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BaseErrors.field("status", "请选择正确的状态");
        }
        LocalDateTime now = BeijingTime.now();
        String action;
        if ("DISABLED".equals(status)) {
            if ("DISABLED".equals(category.getStatus())) {
                throw BaseErrors.stateChanged("该分类已停用");
            }
            if (Boolean.TRUE.equals(category.getIsFallback())) {
                throw BaseErrors.notAllowed("‘" + category.getName() + "’是兜底分类，不能停用");
            }
            if (StringUtils.hasText(category.getAutoSource())) {
                throw BaseErrors.notAllowed("‘" + category.getName() + "’用于自动记账，不能停用");
            }
            if (!StringUtils.hasText(request.getReason())) {
                throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_DISABLE);
            }
            int rows = mapper.update(null, Wrappers.<FinanceCategory>lambdaUpdate()
                    .eq(FinanceCategory::getId, id)
                    .eq(FinanceCategory::getVersion, category.getVersion())
                    .set(FinanceCategory::getStatus, "DISABLED")
                    .set(FinanceCategory::getDisabledAt, now)
                    .set(FinanceCategory::getDisableReason, truncate(request.getReason().trim(), 100))
                    .set(FinanceCategory::getUpdatedBy, adminId)
                    .set(FinanceCategory::getUpdatedAt, now)
                    .set(FinanceCategory::getVersion, category.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            action = "DISABLE";
        } else {
            if ("ENABLED".equals(category.getStatus())) {
                throw BaseErrors.stateChanged("该分类已启用");
            }
            int rows = mapper.update(null, Wrappers.<FinanceCategory>lambdaUpdate()
                    .eq(FinanceCategory::getId, id)
                    .eq(FinanceCategory::getVersion, category.getVersion())
                    .set(FinanceCategory::getStatus, "ENABLED")
                    .set(FinanceCategory::getDisabledAt, null)
                    .set(FinanceCategory::getDisableReason, null)
                    .set(FinanceCategory::getUpdatedBy, adminId)
                    .set(FinanceCategory::getUpdatedAt, now)
                    .set(FinanceCategory::getVersion, category.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            action = "ENABLE";
        }
        FinanceCategory updated = mapper.selectById(id);
        DictItemVO vo = toVO(updated, null, null);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DICT").opAction(action).bizType("FINANCE_CATEGORY").bizId(id)
                .targetName(DictTypes.displayName(type) + " · " + updated.getName()).searchText(updated.getName())
                .reason(request.getReason()).after(Map.of("status", status)).build());
        published(type);
        return vo;
    }

    // ==================== 工具 ====================

    private List<FinanceCategory> list(String type) {
        List<FinanceCategory> list = new ArrayList<>(mapper.selectList(Wrappers.<FinanceCategory>lambdaQuery()
                .eq(FinanceCategory::getDirection, DictTypes.financeDirection(type))));
        list.sort(Comparator.comparing((FinanceCategory c) -> c.getSortNo() == null ? Integer.MAX_VALUE : c.getSortNo())
                .thenComparing(FinanceCategory::getId));
        return list;
    }

    private FinanceCategory require(String type, Long id) {
        FinanceCategory category = id == null ? null : mapper.selectById(id);
        if (category == null || !DictTypes.financeDirection(type).equals(category.getDirection())) {
            throw BaseErrors.notFound();
        }
        return category;
    }

    private static String validateName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw BaseErrors.field("name", "请填写名称");
        }
        String name = rawName.trim();
        if (name.length() > NAME_MAX) {
            throw BaseErrors.field("name", "名称最多 " + NAME_MAX + " 个字");
        }
        return name;
    }

    private static void requireNameUnique(List<FinanceCategory> list, String name, Long excludeId) {
        for (FinanceCategory category : list) {
            if (name.equals(category.getName()) && !Objects.equals(category.getId(), excludeId)) {
                throw BaseErrors.duplicate("已有“" + name + "”");
            }
        }
    }

    /** 管理员新增的编码：EXP_ / INC_ 加 3 位序号，取全表同前缀最大序号 + 1 */
    private String nextCode(String direction) {
        String prefix = "EXPENSE".equals(direction) ? "EXP_" : "INC_";
        int max = 0;
        for (FinanceCategory category : mapper.selectList(null)) {
            String code = category.getCode();
            if (code != null && code.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(code.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非数字后缀的编码不参与取号
                }
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    private static int nextSortNo(List<FinanceCategory> list) {
        int max = 0;
        for (FinanceCategory category : list) {
            if (category.getSortNo() != null && category.getSortNo() < 90) {
                max = Math.max(max, category.getSortNo());
            }
        }
        return max + 1;
    }

    private static Map<Long, Integer> toMap(List<IdCountRow> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (IdCountRow row : rows == null ? List.<IdCountRow>of() : rows) {
            if (row.getId() != null) {
                map.put(row.getId(), row.getCnt() == null ? 0 : row.getCnt());
            }
        }
        return map;
    }

    private static DictItemVO toVO(FinanceCategory category, Integer usageCount, Integer subcategoryCount) {
        DictItemVO vo = new DictItemVO();
        vo.setId(category.getId());
        vo.setCode(category.getCode());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setRemarkRequired(false);
        vo.setIsFallback(Boolean.TRUE.equals(category.getIsFallback()));
        vo.setIsSystem(StringUtils.hasText(category.getAutoSource()));
        vo.setSortNo(category.getSortNo());
        vo.setStatus(category.getStatus());
        vo.setStatusText("DISABLED".equals(category.getStatus()) ? "已停用" : "启用");
        vo.setDisableReason(category.getDisableReason());
        vo.setUsageCount(usageCount == null ? 0 : usageCount);
        vo.setUsageLabel(usageCount == null ? null : usageCount + " 笔");
        vo.setAutoSourceText(autoSourceText(category.getAutoSource()));
        vo.setDescription(autoSourceText(category.getAutoSource()) == null ? null
                : "由" + autoSourceText(category.getAutoSource()) + "自动记账");
        vo.setSubcategoryCount(subcategoryCount);
        vo.setVersion(category.getVersion());
        return vo;
    }

    private static String autoSourceText(String autoSource) {
        if (!StringUtils.hasText(autoSource)) {
            return null;
        }
        return switch (autoSource) {
            case "FEED_INBOUND" -> "饲料入库";
            case "DRUG_INBOUND" -> "兽药疫苗入库";
            case "BATCH_ENTRY" -> "批次入栏";
            case "SALE_RECEIPT" -> "出栏收款";
            case "DRUG_RETURN_REFUND" -> "药苗退货退款";
            default -> autoSource;
        };
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private void published(String type) {
        AfterCommit.run(() -> eventPublisher.publishEvent(BaseDataChangedEvent.dict(type)));
    }
}
