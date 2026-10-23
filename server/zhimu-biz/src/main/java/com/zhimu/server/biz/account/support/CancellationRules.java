package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.CancelBlockReason;
import com.zhimu.server.biz.farm.api.CancelExitFarm;
import com.zhimu.server.biz.farm.api.CancelFarmDisposal;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注销清单口径（模块01 文档 2.7.8）：阻止原因、可注销日期、停用养殖场 180 天清理日与 checklist_version。
 * A02（发注销验证码前）、A19（预览）、A20（提交时重算）共用，避免三处口径不一致。
 */
@Component
@RequiredArgsConstructor
public class CancellationRules {

    public static final String PERSONAL_DATA_DESC = "消息、AI 问答、待办设置、恢复码";

    private final FarmAccountService farmAccountService;
    private final AccountProperties properties;

    /**
     * @param ownedFarms      本人为所有者的养殖场
     * @param exitFarms       注销时将退出的养殖场
     * @param blockReasons    阻止原因，空表示可以注销
     * @param checklistVersion 清单摘要（16 位十六进制）
     * @param farmCleanupDate 停用养殖场 180 天清理日；没有停用养殖场时为 null
     */
    public record CancelCheck(List<CancelOwnedFarm> ownedFarms, List<CancelExitFarm> exitFarms,
                              List<CancelBlockReason> blockReasons, LocalDate cancelAvailableDate,
                              LocalDate farmCleanupDate, String checklistVersion, String activeFarmName,
                              String frozenFarmName) {

        public boolean canCancel() {
            return blockReasons.isEmpty();
        }

        public List<String> blockReasonCodes() {
            return blockReasons.stream().map(Enum::name).toList();
        }
    }

    public CancelCheck evaluate(User user) {
        List<CancelOwnedFarm> owned = farmAccountService.listOwnedFarmsForCancel(user.getId());
        if (owned == null) {
            owned = List.of();
        }
        List<CancelExitFarm> exits = farmAccountService.listExitFarmsForCancel(user.getId());
        if (exits == null) {
            exits = List.of();
        }
        List<CancelBlockReason> reasons = new ArrayList<>();
        LocalDate cancelAvailable = null;
        if (user.getRecoveredAt() != null) {
            cancelAvailable = user.getRecoveredAt().toLocalDate().plusDays(properties.getCancelRestrict().toDays());
            if (!cancelAvailable.isAfter(BeijingTime.today())) {
                cancelAvailable = null;
            } else {
                reasons.add(CancelBlockReason.RECOVERED_WITHIN_7_DAYS);
            }
        }
        String activeName = null;
        String frozenName = null;
        for (CancelOwnedFarm farm : owned) {
            if (FarmStatus.NORMAL.name().equals(farm.farmStatus())) {
                reasons.add(CancelBlockReason.OWNED_FARM_ACTIVE);
                if (activeName == null) {
                    activeName = farm.name();
                }
            } else if (FarmStatus.FROZEN.name().equals(farm.farmStatus())) {
                reasons.add(CancelBlockReason.OWNED_FARM_FROZEN);
                if (frozenName == null) {
                    frozenName = farm.name();
                }
            }
        }
        LocalDate cleanup = farmCleanupDate(owned);
        return new CancelCheck(owned, exits, List.copyOf(new java.util.LinkedHashSet<>(reasons)), cancelAvailable,
                cleanup, checklistVersion(owned, exits), activeName, frozenName);
    }

    /** 停用养殖场中最早到期的清理日（停用时间 + 180 天）；没有停用养殖场时返回 null */
    public LocalDate farmCleanupDate(List<CancelOwnedFarm> owned) {
        List<LocalDateTime> disabledAt = owned.stream()
                .filter(farm -> FarmStatus.DISABLED.name().equals(farm.farmStatus()))
                .map(CancelOwnedFarm::disabledAt)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!disabledAt.isEmpty()) {
            return disabledAt.stream().min(Comparator.naturalOrder()).orElseThrow()
                    .toLocalDate().plusDays(properties.getFarmRetention().toDays());
        }
        boolean hasDisabled = owned.stream().anyMatch(farm -> FarmStatus.DISABLED.name().equals(farm.farmStatus()));
        return hasDisabled ? BeijingTime.today().plusDays(properties.getFarmRetention().toDays()) : null;
    }

    /**
     * 清单摘要：只取养殖场 ID、角色、状态、处置与待确认转让，不含存货、成员数等易变值；
     * 取 SHA-256 前 16 位十六进制。
     */
    public String checklistVersion(List<CancelOwnedFarm> owned, List<CancelExitFarm> exits) {
        StringBuilder sb = new StringBuilder("OWNED");
        owned.stream()
                .sorted(Comparator.comparing(CancelOwnedFarm::farmId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(farm -> sb.append('|').append(farm.farmId())
                        .append('|').append("OWNER")
                        .append('|').append(farm.farmStatus())
                        .append('|').append(farm.disposal() == null ? "" : farm.disposal().name())
                        .append('|').append(farm.transferPendingToName() == null ? "" : farm.transferPendingToName()));
        sb.append(";EXIT");
        exits.stream()
                .sorted(Comparator.comparing(CancelExitFarm::farmId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(farm -> sb.append('|').append(farm.farmId())
                        .append('|').append(farm.role()));
        return Secrets.sha256Hex(sb.toString()).substring(0, 16);
    }

    /** A20 / A02 的 40310：带场名（任务书 7.1 第 5 条） */
    public BizException blockedException(CancelCheck check) {
        String message;
        if (check.activeFarmName() != null) {
            message = check.activeFarmName() + "仍在使用，请先转让给场长或停用后再注销";
        } else if (check.frozenFarmName() != null) {
            message = check.frozenFarmName() + "已被平台冻结，暂时不能注销。解冻后先转让给其他成员或停用，再注销";
        } else if (check.cancelAvailableDate() != null) {
            message = "账号找回后 7 天内不能注销。" + DateTimeText.monthDayWeek(check.cancelAvailableDate()) + "起可以注销";
        } else {
            message = ErrorCode.CANCEL_NOT_ALLOWED.getDefaultMessage();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("block_reasons", check.blockReasonCodes());
        if (check.cancelAvailableDate() != null) {
            data.put("cancel_available_date", BeijingTime.format(check.cancelAvailableDate()));
        }
        return new BizException(ErrorCode.CANCEL_NOT_ALLOWED, message, data);
    }

    /** 可注销日期展示（找回后 7 天内） */
    public String cancelAvailableDateText(CancelCheck check) {
        return check.cancelAvailableDate() == null ? null : BeijingTime.format(check.cancelAvailableDate());
    }

    /** 处置方式为仍在使用/已冻结的养殖场数量 */
    public int blockingFarmCount(CancelCheck check) {
        return (int) check.ownedFarms().stream()
                .filter(farm -> farm.disposal() == CancelFarmDisposal.TRANSFER_OR_DISABLE
                        || farm.disposal() == CancelFarmDisposal.WAIT_UNFREEZE)
                .count();
    }

    public static String disposalText(CancelFarmDisposal disposal) {
        return disposal == null ? null : disposal.getText();
    }

    public static Map<Long, String> roleTexts(List<CancelExitFarm> exits) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (CancelExitFarm exit : exits) {
            map.put(exit.farmId(), exit.roleText());
        }
        return map;
    }

    public boolean isWithinRecoveryRestriction(User user) {
        return UserConvert.cancelRestricted(user, properties);
    }
}
