package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.ExportRequest;
import com.zhimu.server.admin.system.api.ExportService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.mapper.readonly.AdminUserQueryMapper;
import com.zhimu.server.admin.system.query.AdminLoginLogRow;
import com.zhimu.server.admin.system.query.AdminUserFarmRow;
import com.zhimu.server.admin.system.query.AdminUserListRow;
import com.zhimu.server.admin.system.query.AdminUserQuery;
import com.zhimu.server.admin.system.request.UserStatusRequest;
import com.zhimu.server.admin.system.service.UserAdminService;
import com.zhimu.server.admin.system.vo.AdminUserDetailVO;
import com.zhimu.server.admin.system.vo.AdminUserItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.admin.system.vo.PhoneRevealVO;
import com.zhimu.server.biz.account.api.AccountAdminService;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
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

/** ADM-S10–S14：账号查询与导出；停用/启用在写操作前校验文案，再调用模块01 的 AccountAdminService。 */
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");
    private static final String MSG_NOT_FOUND = "该账号不存在或已注销";

    private final AdminUserQueryMapper queryMapper;
    private final AccountAdminService accountAdminService;
    private final ExportService exportService;
    private final OperationLogService operationLogService;

    @Override
    public PageVO<AdminUserItemVO> list(AdminUserQuery query) {
        validateRange(query.getRegisteredFrom(), query.getRegisteredTo());
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        long total = queryMapper.countUsers(status, keyword, query.getRole(),
                query.getRegisteredFrom(), query.getRegisteredTo());
        List<AdminUserItemVO> rows = total == 0 ? List.of() : toItems(queryMapper.selectUsers(status, keyword,
                query.getRole(), query.getRegisteredFrom(), query.getRegisteredTo(), offset, size));
        PageVO<AdminUserItemVO> result = PageVO.of(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("all", (int) queryMapper.countUsers("ALL", keyword, query.getRole(),
                query.getRegisteredFrom(), query.getRegisteredTo()));
        tabs.put("normal", (int) queryMapper.countByStatus("NORMAL", keyword, query.getRole(),
                query.getRegisteredFrom(), query.getRegisteredTo()));
        tabs.put("disabled", (int) queryMapper.countByStatus("DISABLED", keyword, query.getRole(),
                query.getRegisteredFrom(), query.getRegisteredTo()));
        result.setTabCounts(tabs);
        return result;
    }

    @Override
    public AdminUserDetailVO detail(Long userId) {
        User user = userId == null ? null : queryMapper.selectUser(userId);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        LocalDateTime codeCreatedAt = queryMapper.selectLatestRecoveryCodeCreatedAt(user.getId());
        List<AdminUserFarmRow> farms = queryMapper.selectUserFarms(user.getId());
        AdminUserDetailVO.AdminUserDetailVOBuilder builder = AdminUserDetailVO.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(Masks.phone(user.getPhone()))
                .canRevealPhone(canRevealPhone())
                .status(user.getStatus())
                .statusText(userStatusText(user.getStatus()))
                .version(user.getVersion())
                .roleSummary(roleSummary(farms))
                .registeredAt(user.getCreatedAt())
                .registerClientText(AuditTexts.loginClientText(user.getRegisterClient()))
                .recoveryCodeCreatedAt(codeCreatedAt)
                .passwordUpdatedAt(user.getPasswordUpdatedAt())
                .recoveryText(recoveryText(user, queryMapper.selectLatestRecoveryStatus(user.getId())))
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginClientText(AuditTexts.loginClientText(user.getLastLoginClient()))
                .farms(farmDetails(farms))
                .loginLogs(loginLogs(user.getId()))
                .identityTip(identityTip(user, farms));
        return builder.build();
    }

    private boolean canRevealPhone() {
        AdminContext.LoginAdmin admin = AdminContext.get();
        return admin != null && (admin.isSuperAdmin() || admin.hasPermission("WA-S03:HANDLE"));
    }

    @Override
    public String disable(Long userId, UserStatusRequest request) {
        String reason = AuditSupport.requireReason(request.getReason(), "请填写停用原因");
        User user = userId == null ? null : queryMapper.selectUser(userId);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        accountAdminService.disable(userId, AdminContext.requireAdminId(), reason,
                request.getNotifySms() == null || request.getNotifySms(), request.getVersion());
        return user.getName();
    }

    @Override
    public String enable(Long userId, UserStatusRequest request) {
        String reason = AuditSupport.requireReason(request.getReason(), "请填写启用原因");
        User user = userId == null ? null : queryMapper.selectUser(userId);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        accountAdminService.enable(userId, AdminContext.requireAdminId(), reason, request.getVersion());
        return user.getName();
    }

    @Override
    public ExportVO export(AdminUserQuery query) {
        validateRange(query.getRegisteredFrom(), query.getRegisteredTo());
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        long total = queryMapper.countUsers(status, keyword, query.getRole(),
                query.getRegisteredFrom(), query.getRegisteredTo());
        if (total > EXPORT_LIMIT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导出数量超过 10,000 条，请缩小筛选范围");
        }
        List<AdminUserListRow> rows = total == 0 ? List.of()
                : queryMapper.selectUsers(status, keyword, query.getRole(), query.getRegisteredFrom(),
                query.getRegisteredTo(), 0, EXPORT_LIMIT);
        List<String> headers = List.of("用户ID", "姓名", "手机号", "所在养殖场", "角色", "注册时间",
                "最近登录", "状态");
        List<List<Object>> data = new ArrayList<>();
        for (AdminUserListRow row : rows) {
            data.add(List.of(
                    row.getUserId() == null ? "" : row.getUserId(),
                    nullSafe(row.getName()),
                    nullSafe(Masks.phone(row.getPhone())),
                    nullSafe(row.getFarmNamesText()),
                    roleText(row),
                    format(row.getRegisteredAt()),
                    format(row.getLastLoginAt()),
                    userStatusText(row.getStatus())
            ));
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("keyword", keyword);
        filters.put("role", query.getRole());
        return exportService.export(ExportRequest.builder()
                .fileName("用户账号_" + BeijingTime.today().format(DateTimeFormatter.BASIC_ISO_DATE))
                .sheetName("用户账号")
                .headers(headers)
                .rows(data)
                .rowCount(data.size())
                .bizType("USER")
                .filters(filters)
                .build());
    }

    @Override
    public PhoneRevealVO revealPhone(Long userId) {
        User user = userId == null ? null : queryMapper.selectUser(userId);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        String phone = user.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "这个账号没有登记手机号");
        }
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("USER")
                .opAction("VIEW_PHONE")
                .bizType("USER")
                .bizId(user.getId())
                .targetName(user.getName() + " · 手机号 " + Masks.phone(phone))
                .searchText(user.getName())
                .reason("查看完整手机号")
                .after(Map.of("phone_revealed", true))
                .result("SUCCESS")
                .build());
        return PhoneRevealVO.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(phone)
                .build();
    }

    private List<AdminUserItemVO> toItems(List<AdminUserListRow> rows) {
        LocalDateTime now = BeijingTime.now();
        boolean canHandle = canHandle();
        List<AdminUserItemVO> items = new ArrayList<>();
        for (AdminUserListRow row : rows) {
            AdminUserItemVO item = new AdminUserItemVO();
            item.setUserId(row.getUserId());
            item.setName(row.getName());
            item.setPhone(Masks.phone(row.getPhone()));
            item.setFarmNamesText(row.getFarmNamesText());
            item.setFarmCount(row.getFarmCount() == null ? 0 : row.getFarmCount());
            item.getRoleCounts().setOwner(row.getOwnerCount() == null ? 0 : row.getOwnerCount());
            item.getRoleCounts().setManager(row.getManagerCount() == null ? 0 : row.getManagerCount());
            item.getRoleCounts().setStaff(row.getStaffCount() == null ? 0 : row.getStaffCount());
            item.setRegisteredAt(row.getRegisteredAt());
            item.setLastLoginAt(row.getLastLoginAt());
            item.setLastLoginClient(row.getLastLoginClient());
            item.setLastLoginClientText(AuditTexts.loginClientText(row.getLastLoginClient()));
            item.setStatus(row.getStatus());
            item.setStatusText(userStatusText(row.getStatus()));
            item.setVersion(row.getVersion());
            List<AdminUserItemVO.Flag> flags = new ArrayList<>();
            if (row.getFarmPending() != null && row.getFarmPending() > 0) {
                flags.add(AdminUserItemVO.Flag.builder().code("FARM_PENDING").text("养殖场待审核").build());
            }
            if (row.getRecoveryPending() != null && row.getRecoveryPending() > 0) {
                flags.add(AdminUserItemVO.Flag.builder().code("RECOVERY_PENDING").text("找回待审核").build());
            }
            if (row.getRecoveryLockUntil() != null && row.getRecoveryLockUntil().isAfter(now)) {
                flags.add(AdminUserItemVO.Flag.builder().code("RECOVERY_LOCKED").text("找回已锁定").build());
            }
            if (row.getRecoveredAt() != null && row.getRecoveredAt().isAfter(now.minusDays(30))) {
                flags.add(AdminUserItemVO.Flag.builder().code("RECOVERED")
                        .text(row.getRecoveredAt().format(MONTH_DAY) + "找回").build());
            }
            item.setFlags(flags);
            List<String> actions = new ArrayList<>();
            actions.add("DETAIL");
            if (canHandle) {
                actions.add("DISABLED".equals(row.getStatus()) ? "ENABLE" : "DISABLE");
            }
            item.setActions(actions);
            items.add(item);
        }
        return items;
    }

    private List<AdminUserDetailVO.Farm_> farmDetails(List<AdminUserFarmRow> rows) {
        List<AdminUserDetailVO.Farm_> farms = new ArrayList<>();
        for (AdminUserFarmRow row : rows) {
            String status = AuditTexts.farmStatus(row.getAuditStatus(), row.getDisabled(), row.getFrozen());
            farms.add(AdminUserDetailVO.Farm_.builder()
                    .farmId(row.getFarmId())
                    .farmName(row.getFarmName())
                    .role(row.getRole())
                    .roleText(AuditTexts.roleText(row.getRole()))
                    .farmStatus(status)
                    .farmStatusText(AuditTexts.farmStatusText(row.getAuditStatus(), row.getDisabled(), row.getFrozen()))
                    .scaleText(scaleText(row, status))
                    .stockCount(row.getStockCount())
                    .countUnitText(countUnitText(row.getCountUnit()))
                    .penCount(row.getPenCount())
                    .build());
        }
        return farms;
    }

    private List<AdminUserDetailVO.LoginLog> loginLogs(Long userId) {
        List<AdminUserDetailVO.LoginLog> logs = new ArrayList<>();
        for (AdminLoginLogRow row : queryMapper.selectLoginLogs(userId,
                BeijingTime.now().minusDays(90), 50)) {
            logs.add(AdminUserDetailVO.LoginLog.builder()
                    .time(row.getTime())
                    .clientText(AuditTexts.loginClientText(row.getClientType()))
                    .ipRegion(row.getIpRegion())
                    .result(row.getResult())
                    .resultText("SUCCESS".equals(row.getResult()) ? "成功" : "失败")
                    .build());
        }
        return logs;
    }

    private static String roleSummary(List<AdminUserFarmRow> farms) {
        int owner = 0;
        int manager = 0;
        int staff = 0;
        for (AdminUserFarmRow row : farms) {
            switch (row.getRole()) {
                case "OWNER" -> owner++;
                case "MANAGER" -> manager++;
                case "STAFF" -> staff++;
                default -> {
                }
            }
        }
        if (owner + manager + staff == 0) {
            return "未加入养殖场";
        }
        List<String> parts = new ArrayList<>();
        if (owner > 0) {
            parts.add("所有者 " + owner + " 个");
        }
        if (manager > 0) {
            parts.add("场长 " + manager + " 个");
        }
        if (staff > 0) {
            parts.add("员工 " + staff + " 个");
        }
        return String.join("、", parts);
    }

    private static String scaleText(AdminUserFarmRow row, String farmStatus) {
        if ("STAFF".equals(row.getRole()) && row.getPenCount() != null && row.getPenCount() > 0) {
            return "负责 " + row.getPenCount() + " 个栏舍";
        }
        if ("NORMAL".equals(farmStatus) || "DISABLED".equals(farmStatus) || "FROZEN".equals(farmStatus)) {
            return "存栏 " + (row.getStockCount() == null ? 0 : row.getStockCount()) + " "
                    + countUnitText(row.getCountUnit());
        }
        return "规模 " + (row.getScale() == null ? 0 : row.getScale()) + " " + countUnitText(row.getCountUnit());
    }

    private static String identityTip(User user, List<AdminUserFarmRow> farms) {
        if (farms.isEmpty()) {
            return user.getName() + "还没有加入养殖场。停用账号后不能登录 App 和网页版，已登录的设备立即退出。";
        }
        AdminUserFarmRow primary = farms.get(0);
        String roleText = AuditTexts.roleText(primary.getRole());
        String farmName = primary.getFarmName();
        if ("OWNER".equals(primary.getRole())) {
            return user.getName() + "是" + farmName + "的所有者，停用账号不会冻结养殖场；需要时请到养殖场管理中冻结。";
        }
        return user.getName() + "是" + farmName + "的" + roleText + "。停用账号不影响" + farmName
                + "的其他成员，也不会冻结养殖场。";
    }

    private static String recoveryText(User user, String latestStatus) {
        if (user.getRecoveredAt() != null) {
            return user.getRecoveredAt().format(MONTH_DAY) + "已找回";
        }
        if (latestStatus == null) {
            return "没有记录";
        }
        return switch (latestStatus) {
            case "PENDING" -> "找回待审核";
            case "APPROVED" -> "已通过找回申请";
            case "REJECTED" -> "最近一次找回已驳回";
            default -> latestStatus;
        };
    }

    private static String roleText(AdminUserListRow row) {
        List<String> parts = new ArrayList<>();
        if (row.getOwnerCount() != null && row.getOwnerCount() > 0) {
            parts.add("所有者 " + row.getOwnerCount());
        }
        if (row.getManagerCount() != null && row.getManagerCount() > 0) {
            parts.add("场长 " + row.getManagerCount());
        }
        if (row.getStaffCount() != null && row.getStaffCount() > 0) {
            parts.add("员工 " + row.getStaffCount());
        }
        return parts.isEmpty() ? "未加入养殖场" : String.join("、", parts);
    }

    private static String userStatusText(String status) {
        return "NORMAL".equals(status) ? "正常" : "DISABLED".equals(status) ? "已停用" : status;
    }

    private static String countUnitText(String unit) {
        return "HEAD".equals(unit) ? "头" : "BIRD".equals(unit) ? "只" : nullSafe(unit);
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : BeijingTime.format(time);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return;
        }
        if (from == null || to == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择完整的注册时间范围");
        }
        if (from.isAfter(to)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BizException(ErrorCode.PARAM_INVALID, "注册时间范围不能超过 1 年");
        }
    }

    private static boolean canHandle() {
        AdminContext.LoginAdmin admin = AdminContext.get();
        return admin != null && admin.hasPermission("WA-S03:HANDLE");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
