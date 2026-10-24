package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.system.api.ExportRequest;
import com.zhimu.server.admin.system.api.ExportService;
import com.zhimu.server.admin.system.mapper.readonly.AdminFarmQueryMapper;
import com.zhimu.server.admin.system.query.AdminFarmMemberRow;
import com.zhimu.server.admin.system.query.AdminFarmOwnerRow;
import com.zhimu.server.admin.system.query.AdminFarmQuery;
import com.zhimu.server.admin.system.query.AdminFarmTimelineRow;
import com.zhimu.server.admin.system.request.FarmFreezeRequest;
import com.zhimu.server.admin.system.service.FarmAdminService;
import com.zhimu.server.admin.system.vo.AdminFarmDetailVO;
import com.zhimu.server.admin.system.vo.AdminFarmItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.admin.system.vo.FreezeResultVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.FarmPlatformService;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADM-S15–S19：列表与详情只读汇总；冻结/解冻调用模块02 的 FarmPlatformService。 */
@Service
@RequiredArgsConstructor
public class FarmAdminServiceImpl implements FarmAdminService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final String MSG_NOT_FOUND = "该养殖场不存在";
    private static final String MSG_DELETED = "该养殖场已被删除，无法操作";

    private final AdminFarmQueryMapper queryMapper;
    private final BaseDataQueryService baseData;
    private final BizMediaService bizMediaService;
    private final FarmPlatformService farmPlatformService;
    private final ExportService exportService;
    private final AccountQueryService accountQueryService;

    @Override
    public PageVO<AdminFarmItemVO> list(AdminFarmQuery query) {
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        String provincePrefix = AuditSupport.provincePrefix(query.getProvinceCode());
        LocalDate yesterday = BeijingTime.today().minusDays(1);
        LocalDate start = yesterday.minusDays(6);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        long total = queryMapper.countFarms(status, keyword, query.getCategoryId(), provincePrefix);
        List<AdminFarmItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectFarms(status, keyword, query.getCategoryId(), provincePrefix,
                start, yesterday, offset, size);
        for (AdminFarmItemVO row : rows) {
            decorate(row);
        }
        PageVO<AdminFarmItemVO> result = PageVO.of(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("all", (int) queryMapper.countFarms("ALL", keyword, query.getCategoryId(), provincePrefix));
        for (String code : List.of("NORMAL", "DISABLED", "FROZEN", "PENDING", "REJECTED")) {
            tabs.put(code.toLowerCase(), (int) queryMapper.countFarmsByStatus(code, keyword,
                    query.getCategoryId(), provincePrefix));
        }
        result.setTabCounts(tabs);
        return result;
    }

    @Override
    public AdminFarmDetailVO detail(Long farmId) {
        Farm farm = farmId == null ? null : queryMapper.selectFarm(farmId);
        if (farm == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        AdminFarmOwnerRow owner = queryMapper.selectOwner(farm.getId());
        CategoryInfo category = baseData.getCategory(farm.getMainCategoryId());
        String countUnit = category == null ? null : category.countUnitText();
        LocalDate yesterday = BeijingTime.today().minusDays(1);
        LocalDate start = yesterday.minusDays(6);
        Long pendingChangeId = queryMapper.selectPendingChangeId(farm.getId());
        List<String> tags = new ArrayList<>();
        String auditText = "APPROVED".equals(farm.getAuditStatus()) ? "已通过"
                : "PENDING".equals(farm.getAuditStatus()) ? "待审核" : "已驳回";
        tags.add(auditText);
        if ("APPROVED".equals(farm.getAuditStatus())) {
            if (Boolean.TRUE.equals(farm.getFrozen())) {
                tags.add("已冻结");
            } else if (Boolean.TRUE.equals(farm.getDisabled())) {
                tags.add("已停用");
            } else {
                tags.add("正常");
            }
        }
        if (pendingChangeId != null) {
            tags.add("资料变更待审核");
        }
        String frozenByName = farm.getFrozenBy() == null ? null
                : queryMapper.selectAdminName(farm.getFrozenBy());
        int memberTotal = value(queryMapper.selectMemberTotal(farm.getId()));
        return AdminFarmDetailVO.builder()
                .farmId(farm.getId())
                .name(farm.getName())
                .categoryName(category == null ? null : category.name())
                .owner(AdminFarmDetailVO.Owner.builder()
                        .name(owner == null ? farm.getContactName() : owner.getName())
                        .phone(Masks.phone(owner == null ? farm.getContactPhone() : owner.getPhone()))
                        .build())
                .address(farm.getAddress())
                .tags(tags)
                .summary(AdminFarmDetailVO.Summary.builder()
                        .stockCount(value(queryMapper.selectStockCount(farm.getId())))
                        .stockUnit(countUnit)
                        .activeBatchCount(value(queryMapper.selectActiveBatchCount(farm.getId())))
                        .records7d(value(queryMapper.selectRecords7d(farm.getId(), start, yesterday)))
                        .activeMembers7d(value(queryMapper.selectActiveMembers7d(farm.getId(), start, yesterday)))
                        .memberTotal(memberTotal)
                        .build())
                .info(AdminFarmDetailVO.Info.builder()
                        .scaleText("设计存栏 " + (farm.getScale() == null ? 0 : farm.getScale()) + " " + countUnit)
                        .qualImages(bizMediaService.list(BizType.FARM, farm.getId()))
                        .contactName(farm.getContactName())
                        .contactPhone(Masks.phone(farm.getContactPhone()))
                        .createdAt(farm.getCreatedAt())
                        .build())
                .members(members(farm.getId()))
                .invites(AdminFarmDetailVO.Invites.builder()
                        .pending(queryMapper.countInvites(farm.getId(), "PENDING"))
                        .expired(queryMapper.countInvites(farm.getId(), "EXPIRED"))
                        .build())
                .timeline(timeline(farm.getId()))
                .frozen(farm.getFrozenAt() == null ? null : AdminFarmDetailVO.Frozen.builder()
                        .frozenAt(farm.getFrozenAt())
                        .frozenReason(farm.getFrozenReason())
                        .frozenByName(frozenByName)
                        .build())
                .pendingChangeId(pendingChangeId)
                .version(farm.getVersion())
                .build();
    }

    @Override
    public FreezeResultVO freeze(Long farmId, FarmFreezeRequest request) {
        String reason = AuditSupport.requireReason(request.getReason(), "请填写冻结原因");
        Farm farm = requireFarm(farmId);
        farmPlatformService.freeze(farmId, AdminContext.requireAdminId(), reason,
                Boolean.TRUE.equals(request.getSmsOwner()), request.getVersion());
        int members = farmPlatformService.listActiveUserIds(farmId).size();
        return new FreezeResultVO(members, farm.getName());
    }

    @Override
    public String unfreeze(Long farmId, FarmFreezeRequest request) {
        String reason = AuditSupport.requireReason(request.getReason(), "请填写解冻原因");
        Farm farm = requireFarm(farmId);
        farmPlatformService.unfreeze(farmId, AdminContext.requireAdminId(), reason, request.getVersion());
        return farm.getName();
    }

    @Override
    public ExportVO export(AdminFarmQuery query) {
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        String provincePrefix = AuditSupport.provincePrefix(query.getProvinceCode());
        long total = queryMapper.countFarms(status, keyword, query.getCategoryId(), provincePrefix);
        if (total > EXPORT_LIMIT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导出数量超过 10,000 条，请缩小筛选范围");
        }
        LocalDate yesterday = BeijingTime.today().minusDays(1);
        LocalDate start = yesterday.minusDays(6);
        List<AdminFarmItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectFarms(status, keyword, query.getCategoryId(), provincePrefix,
                start, yesterday, 0, EXPORT_LIMIT);
        List<String> headers = List.of("养殖场ID", "名称", "品类", "所有者", "手机号", "地区", "成员数",
                "存栏", "审核通过时间", "近7天登记", "状态");
        List<List<Object>> data = new ArrayList<>();
        for (AdminFarmItemVO row : rows) {
            decorate(row);
            data.add(List.of(
                    row.getFarmId() == null ? "" : row.getFarmId(),
                    nullSafe(row.getName()),
                    nullSafe(row.getCategoryName()),
                    nullSafe(row.getOwnerName()),
                    nullSafe(row.getOwnerPhone()),
                    nullSafe(row.getRegionText()),
                    row.getMemberCount() == null ? 0 : row.getMemberCount(),
                    (row.getStockCount() == null ? 0 : row.getStockCount()) + " " + nullSafe(row.getStockUnit()),
                    format(row.getApprovedAt()),
                    row.getRecords7d() == null ? "—" : row.getRecords7d(),
                    nullSafe(row.getFarmStatusText())
            ));
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("keyword", keyword);
        filters.put("category_id", query.getCategoryId());
        filters.put("province_code", query.getProvinceCode());
        return exportService.export(ExportRequest.builder()
                .fileName("养殖场_" + BeijingTime.today().format(DateTimeFormatter.BASIC_ISO_DATE))
                .sheetName("养殖场")
                .headers(headers)
                .rows(data)
                .rowCount(data.size())
                .bizType("FARM")
                .filters(filters)
                .build());
    }

    private void decorate(AdminFarmItemVO row) {
        row.setOwnerPhone(Masks.phone(row.getOwnerPhone()));
        row.setFarmStatus(AuditTexts.farmStatus(row.getAuditStatus(), row.getDisabled(), row.getFrozen()));
        row.setFarmStatusText(AuditTexts.farmStatusText(row.getAuditStatus(), row.getDisabled(), row.getFrozen()));
        row.setChangePending(row.getPendingChangeId() != null);
        if ("FROZEN".equals(row.getFarmStatus()) || !"APPROVED".equals(row.getAuditStatus())) {
            row.setRecords7d(null);
        }
        AdminContext.LoginAdmin admin = AdminContext.get();
        boolean handle = admin != null && admin.hasPermission("WA-S04:HANDLE");
        boolean auditView = admin != null && admin.hasPermission("WA-R01:VIEW");
        boolean changeView = admin != null && admin.hasPermission("WA-R02:VIEW");
        List<String> actions = new ArrayList<>();
        actions.add("DETAIL");
        switch (row.getFarmStatus()) {
            case "FROZEN" -> {
                if (handle) {
                    actions.add("UNFREEZE");
                }
            }
            case "PENDING" -> {
                if (auditView) {
                    actions.add("GO_AUDIT");
                }
            }
            case "REJECTED" -> {
            }
            default -> {
                if (handle) {
                    actions.add("FREEZE");
                }
            }
        }
        if (row.getPendingChangeId() != null && changeView) {
            actions.add("VIEW_CHANGE");
        }
        row.setActions(actions);
    }

    private List<AdminFarmDetailVO.Member> members(Long farmId) {
        List<AdminFarmDetailVO.Member> members = new ArrayList<>();
        for (AdminFarmMemberRow row : queryMapper.selectMembers(farmId)) {
            String penText = row.getPenText() != null ? row.getPenText()
                    : ("STAFF".equals(row.getRole()) ? "未分配栏舍" : "全部栏舍");
            members.add(AdminFarmDetailVO.Member.builder()
                    .userId(row.getUserId())
                    .name(row.getName())
                    .avatarUrl(accountQueryService.avatarUrl(row.getUserId()))
                    .phone(Masks.phone(row.getPhone()))
                    .role(row.getRole())
                    .roleText(AuditTexts.roleText(row.getRole()))
                    .penText(penText)
                    .joinedAt(row.getJoinedAt())
                    .lastLoginAt(row.getLastLoginAt())
                    .build());
        }
        return members;
    }

    private List<AdminFarmDetailVO.Timeline> timeline(Long farmId) {
        List<AdminFarmDetailVO.Timeline> timeline = new ArrayList<>();
        for (AdminFarmTimelineRow row : queryMapper.selectTimeline(farmId)) {
            String tag = switch (row.getTag() == null ? "" : row.getTag()) {
                case "APPROVED" -> "已通过";
                case "PENDING" -> "待审核";
                case "REJECTED" -> "已驳回";
                case "SUCCESS" -> "成功";
                case "FAIL" -> "失败";
                default -> row.getTag();
            };
            timeline.add(AdminFarmDetailVO.Timeline.builder()
                    .event(row.getEvent())
                    .time(row.getTime())
                    .actor(row.getActor())
                    .tag(tag)
                    .desc(row.getDescription())
                    .build());
        }
        return timeline;
    }

    private Farm requireFarm(Long farmId) {
        Farm farm = farmId == null ? null : queryMapper.selectFarm(farmId);
        if (farm == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        return farm;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : BeijingTime.format(time);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
