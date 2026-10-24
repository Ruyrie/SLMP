package com.zhimu.server.admin.audit.support;

import com.zhimu.server.common.util.BeijingTime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 审核等待时长与超时标记（管理端-01 2.7）：超过 48 小时为 overdue，恰好 48 小时不算。
 * 满 1 天写“2 天 18 小时”，满 1 小时写“1 小时 50 分”，否则写“39 分钟”。
 */
public final class AuditWaitCalculator {

    public static final int OVERDUE_MINUTES = 48 * 60;

    private AuditWaitCalculator() {
    }

    public static Wait of(LocalDateTime submittedAt) {
        if (submittedAt == null) {
            return new Wait(0, "0 分钟", false);
        }
        return of(submittedAt, BeijingTime.now());
    }

    public static Wait of(LocalDateTime submittedAt, LocalDateTime now) {
        if (submittedAt == null) {
            return new Wait(0, "0 分钟", false);
        }
        long minutes = Math.max(0, Duration.between(submittedAt, now).toMinutes());
        return new Wait((int) Math.min(Integer.MAX_VALUE, minutes), text(minutes), minutes > OVERDUE_MINUTES);
    }

    public static String text(long minutes) {
        long safe = Math.max(0, minutes);
        if (safe >= 24 * 60) {
            long days = safe / (24 * 60);
            long hours = (safe % (24 * 60)) / 60;
            return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
        }
        if (safe >= 60) {
            long hours = safe / 60;
            long mins = safe % 60;
            return mins == 0 ? hours + " 小时" : hours + " 小时 " + mins + " 分";
        }
        return safe + " 分钟";
    }

    public record Wait(int minutes, String text, boolean overdue) {
    }
}
