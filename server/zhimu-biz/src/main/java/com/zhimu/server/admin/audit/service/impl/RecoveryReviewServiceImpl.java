package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.query.RecoveryFarmRow;
import com.zhimu.server.admin.audit.query.RecoveryVerifyLogRow;
import com.zhimu.server.admin.audit.request.RecoveryHandleRequest;
import com.zhimu.server.admin.audit.service.RecoveryReviewService;
import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.audit.support.AuditWaitCalculator;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.RecoveryDetailVO;
import com.zhimu.server.admin.audit.vo.RecoveryItemVO;
import com.zhimu.server.biz.account.api.AccountRecoveryAdminService;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.RecoveryVerifyResult;
import com.zhimu.server.biz.account.enums.SmsSendStatus;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADM-R14–R17：找回队列只含恢复码已核对正确的申请；通过/驳回复用模块01 的找回 Service。 */
@Service
@RequiredArgsConstructor
public class RecoveryReviewServiceImpl implements RecoveryReviewService {

    private static final String MSG_NOT_FOUND = "这条找回申请不存在，列表已刷新";
    private static final String DEFAULT_SMS_WARNING = "临时密码短信发送失败，系统会自动重试；如长时间未收到，请联系管理员重新发送。";

    private final AuditAdminQueryMapper queryMapper;
    private final AccountRecoveryAdminService accountRecoveryAdminService;

    @Override
    public PageVO<RecoveryItemVO> list(RecoveryAdminQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String tab = query.tabOrDefault();
        String keyword = trimToNull(query.getKeyword());
        long total = queryMapper.countRecoveries(tab, keyword);
        List<RecoveryItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectRecoveries(tab, keyword, offset, size);
        LocalDateTime now = BeijingTime.now();
        for (RecoveryItemVO row : rows) {
            row.setOldPhone(Masks.phone(row.getOldPhone()));
            row.setNewPhone(Masks.phone(row.getNewPhone()));
            row.setStatusText(AuditTexts.statusText(row.getStatus()));
            String channel = AuditTexts.submitChannelText(row.getSubmitChannel());
            if ("CS_PROXY".equals(row.getSubmitChannel()) && row.getProxyAdminName() != null) {
                channel = channel + " · " + row.getProxyAdminName();
            }
            row.setSubmitChannelText(channel);
            AuditWaitCalculator.Wait wait = AuditWaitCalculator.of(row.getSubmittedAt(), now);
            row.setWaitMinutes(wait.minutes());
            row.setWaitText(wait.text());
            row.setOverdue(wait.overdue());
        }
        PageVO<RecoveryItemVO> result = PageVO.of(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("PENDING", (int) queryMapper.countRecoveryByStatus("PENDING"));
        tabs.put("HANDLED", (int) (queryMapper.countRecoveryByStatus("APPROVED")
                + queryMapper.countRecoveryByStatus("REJECTED")));
        result.setTabCounts(tabs);
        return result;
    }

    @Override
    public RecoveryDetailVO detail(Long id) {
        AccountRecovery recovery = id == null ? null : queryMapper.selectAccountRecovery(id);
        if (recovery == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        User user = queryMapper.selectUser(recovery.getUserId());
        User occupied = queryMapper.selectUserByPhone(recovery.getNewPhone());
        LocalDateTime now = BeijingTime.now();
        long otherRecoveries = queryMapper.countUserRecoveriesSince(recovery.getUserId(),
                now.minusDays(30), recovery.getId());
        LocalDateTime codeCreatedAt = queryMapper.selectLatestRecoveryCodeCreatedAt(recovery.getUserId());
        List<RecoveryFarmRow> farms = queryMapper.selectRecoveryFarms(recovery.getUserId());
        List<RecoveryVerifyLogRow> logs = queryMapper.selectRecoveryVerifyLogs(recovery.getUserId(),
                recovery.getOldPhone(), 20);
        List<RecoveryDetailVO.CheckItem> checks = checks(recovery, user, occupied, otherRecoveries);
        return RecoveryDetailVO.builder()
                .id(recovery.getId())
                .userId(recovery.getUserId())
                .userName(user == null ? null : user.getName())
                .status(recovery.getStatus())
                .statusText(AuditTexts.statusText(recovery.getStatus()))
                .version(recovery.getVersion())
                .oldPhone(Masks.phone(recovery.getOldPhone()))
                .newPhone(Masks.phone(recovery.getNewPhone()))
                .newPhoneVerified(recovery.getNewPhoneVerifiedAt() != null)
                .codeAttemptNo(recovery.getCodeAttemptNo())
                .codeFailCount(recovery.getCodeFailCount())
                .submittedAt(recovery.getCreatedAt())
                .checks(checks)
                .description(recovery.getDescription())
                .account(RecoveryDetailVO.Account.builder()
                        .registeredAt(user == null ? null : user.getCreatedAt())
                        .recoveryCodeCreatedAt(codeCreatedAt)
                        .recoveredAt(user == null ? null : user.getRecoveredAt())
                        .lastLoginAt(user == null ? null : user.getLastLoginAt())
                        .lastLoginClientText(user == null ? null : AuditTexts.loginClientText(user.getLastLoginClient()))
                        .build())
                .farms(farmList(farms))
                .channel(RecoveryDetailVO.Channel.builder()
                        .submitChannel(recovery.getSubmitChannel())
                        .submitChannelText(AuditTexts.submitChannelText(recovery.getSubmitChannel()))
                        .submitDevice(recovery.getSubmitDevice())
                        .submitIpRegion(recovery.getSubmitIpRegion())
                        .build())
                .afterActions(RecoveryDetailVO.AfterActions.builder()
                        .newPhone(Masks.phone(recovery.getNewPhone()))
                        .cancelForbiddenUntil(recovery.getHandledAt() == null
                                ? BeijingTime.today().plusDays(7)
                                : recovery.getHandledAt().toLocalDate().plusDays(7))
                        .build())
                .verifyMethods(verifyMethods())
                .verifyMethod(recovery.getVerifyMethod())
                .verifyMethodText(AuditTexts.verifyMethodText(recovery.getVerifyMethod()))
                .handleNote(recovery.getHandleNote())
                .handledByName(recovery.getHandledBy() == null ? null
                        : queryMapper.selectAdminName(recovery.getHandledBy()))
                .handledAt(recovery.getHandledAt())
                .sms(RecoveryDetailVO.Sms.builder()
                        .status(recovery.getResultSmsStatus())
                        .statusText(AuditTexts.smsStatusText(recovery.getResultSmsStatus()))
                        .retryCount(recovery.getResultSmsRetryCount())
                        .warning(SmsSendStatus.FAILED.name().equals(recovery.getResultSmsStatus())
                                ? DEFAULT_SMS_WARNING : null)
                        .build())
                .canRevealPhone(canRevealPhone())
                .verifyLogs(verifyLogs(logs))
                .build();
    }

    @Override
    public AuditResultVO approve(Long id, RecoveryHandleRequest request) {
        String note = AuditSupport.requireReason(request.getHandleNote(), "请填写处理说明");
        Target target = target(id);
        accountRecoveryAdminService.approve(id, AdminContext.requireAdminId(), request.getVerifyMethod(),
                note, request.getVersion());
        return AuditResultVO.builder()
                .targetName(target.userName())
                .notifiedNames(target.userName() == null ? List.of() : List.of(target.userName()))
                .smsPhone(Masks.phone(target.recovery().getNewPhone()))
                .build();
    }

    @Override
    public AuditResultVO reject(Long id, RecoveryHandleRequest request) {
        String note = AuditSupport.requireReason(request.getHandleNote(), "请填写处理说明");
        Target target = target(id);
        accountRecoveryAdminService.reject(id, AdminContext.requireAdminId(), request.getVerifyMethod(),
                note, request.getVersion());
        return AuditResultVO.builder()
                .targetName(target.userName())
                .notifiedNames(target.userName() == null ? List.of() : List.of(target.userName()))
                .smsPhone(Masks.phone(target.recovery().getNewPhone()))
                .build();
    }

    private Target target(Long id) {
        AccountRecovery recovery = id == null ? null : queryMapper.selectAccountRecovery(id);
        if (recovery == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        User user = queryMapper.selectUser(recovery.getUserId());
        return new Target(recovery, user == null ? null : user.getName());
    }

    private List<RecoveryDetailVO.CheckItem> checks(AccountRecovery recovery, User user,
                                                    User occupied, long otherRecoveries) {
        List<RecoveryDetailVO.CheckItem> checks = new ArrayList<>();
        int attempt = recovery.getCodeAttemptNo() == null ? 1 : recovery.getCodeAttemptNo();
        int fails = recovery.getCodeFailCount() == null ? Math.max(0, attempt - 1) : recovery.getCodeFailCount();
        checks.add(RecoveryDetailVO.CheckItem.builder()
                .code("RECOVERY_CODE")
                .level("PASS")
                .text("恢复码正确")
                .extra("第 " + attempt + " 次填写" + (fails > 0 ? "（错 " + fails + " 次）" : ""))
                .build());
        checks.add(RecoveryDetailVO.CheckItem.builder()
                .code("NEW_PHONE_VERIFIED")
                .level("PASS")
                .text("新手机号 " + Masks.phone(recovery.getNewPhone()) + " 已通过短信验证")
                .extra(BeijingTime.format(recovery.getNewPhoneVerifiedAt()))
                .build());
        boolean occupiedByOther = occupied != null && !occupied.getId().equals(recovery.getUserId());
        checks.add(RecoveryDetailVO.CheckItem.builder()
                .code("NEW_PHONE_AVAILABLE")
                .level(occupiedByOther ? "WARN" : "PASS")
                .text(occupiedByOther ? "新手机号已被其他账号注册，不能通过" : "新手机号没有注册其他账号")
                .build());
        checks.add(RecoveryDetailVO.CheckItem.builder()
                .code("RECENT_RECOVERY")
                .level(otherRecoveries > 0 ? "WARN" : "PASS")
                .text(otherRecoveries > 0
                        ? "该账号近 30 天还有 " + otherRecoveries + " 条找回申请，请核实原因"
                        : "该账号近 30 天没有其他找回申请")
                .build());
        if (user != null) {
            String accountState = switch (user.getStatus() == null ? "" : user.getStatus()) {
                case "NORMAL" -> "账号状态正常";
                case "DISABLED" -> "账号已被平台停用";
                case "CANCELLED" -> "账号已注销";
                default -> "账号状态：" + user.getStatus();
            };
            checks.add(RecoveryDetailVO.CheckItem.builder()
                    .code("ACCOUNT_STATE")
                    .level(UserStatus.NORMAL.name().equals(user.getStatus()) ? "PASS" : "WARN")
                    .text(accountState)
                    .build());
            checks.add(RecoveryDetailVO.CheckItem.builder()
                    .code("LAST_LOGIN")
                    .level("PASS")
                    .text(user.getLastLoginAt() == null ? "原手机号没有登录记录"
                            : "原手机号最近一次登录：" + BeijingTime.format(user.getLastLoginAt())
                            + "（" + AuditTexts.loginClientText(user.getLastLoginClient()) + "）")
                    .build());
        }
        return checks;
    }

    private static List<RecoveryDetailVO.FarmRoleInfo> farmList(List<RecoveryFarmRow> rows) {
        List<RecoveryDetailVO.FarmRoleInfo> farms = new ArrayList<>();
        for (RecoveryFarmRow row : rows) {
            farms.add(RecoveryDetailVO.FarmRoleInfo.builder()
                    .farmId(row.getFarmId())
                    .farmName(row.getFarmName())
                    .role(row.getRole())
                    .roleText(AuditTexts.roleText(row.getRole()))
                    .penText(row.getPenText())
                    .build());
        }
        return farms;
    }

    private static List<RecoveryDetailVO.Option> verifyMethods() {
        return List.of(
                RecoveryDetailVO.Option.builder().value("CALLBACK_NEW_PHONE").label("回拨新手机号，本人确认").build(),
                RecoveryDetailVO.Option.builder().value("CONTACT_FARM_OWNER").label("联系养殖场所有者确认身份").build(),
                RecoveryDetailVO.Option.builder().value("OTHER").label("其他方式").build()
        );
    }

    private static List<RecoveryDetailVO.VerifyLog> verifyLogs(List<RecoveryVerifyLogRow> rows) {
        List<RecoveryDetailVO.VerifyLog> logs = new ArrayList<>();
        for (RecoveryVerifyLogRow row : rows) {
            String channel = AuditTexts.submitChannelText(row.getSubmitChannel());
            if ("CS_PROXY".equals(row.getSubmitChannel()) && row.getProxyAdminName() != null) {
                channel = channel + " · " + row.getProxyAdminName();
            }
            logs.add(RecoveryDetailVO.VerifyLog.builder()
                    .checkedAt(row.getCheckedAt())
                    .result(row.getResult())
                    .resultText(AuditTexts.recoveryResultText(row.getResult()))
                    .resultDesc(resultDesc(row))
                    .submitChannelText(channel)
                    .remainingAttempts(row.getRemainingAttempts())
                    .lockUntil(row.getLockUntil())
                    .build());
        }
        return logs;
    }

    private static String resultDesc(RecoveryVerifyLogRow row) {
        if (RecoveryVerifyResult.CODE_CORRECT.name().equals(row.getResult())) {
            return "进入人工核实";
        }
        if (RecoveryVerifyResult.CODE_WRONG.name().equals(row.getResult())) {
            return row.getRemainingAttempts() == null ? "恢复码不正确"
                    : "还可以再试 " + row.getRemainingAttempts() + " 次";
        }
        if (RecoveryVerifyResult.LOCKED.name().equals(row.getResult())) {
            return "连续填错 5 次" + (row.getLockUntil() == null ? ""
                    : "，" + BeijingTime.format(row.getLockUntil()) + " 后才能再次提交");
        }
        return "这个手机号没有注册过账号，不计入填错次数";
    }

    private static boolean canRevealPhone() {
        // R24（查看完整号码）不在本期范围，避免前端显示一个没有后端的入口。
        return false;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Target(AccountRecovery recovery, String userName) {
    }
}
