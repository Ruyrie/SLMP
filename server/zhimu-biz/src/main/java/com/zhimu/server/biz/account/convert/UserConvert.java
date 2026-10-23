package com.zhimu.server.biz.account.convert;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.biz.account.vo.RecoveryInfoVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;

import java.time.LocalDate;

/** User → VO：手机号打码、姓名首字、账号找回信息与可注销日期。 */
public final class UserConvert {

    private UserConvert() {
    }

    public static AccountUserVO toAccountUser(User user) {
        if (user == null) {
            return null;
        }
        return AccountUserVO.builder()
                .userId(user.getId())
                .name(user.getName())
                .nameInitial(initial(user.getName()))
                .phoneMasked(Masks.phone(user.getPhone()))
                .build();
    }

    public static ProfileVO toProfile(User user) {
        return ProfileVO.builder()
                .name(user.getName())
                .nameInitial(initial(user.getName()))
                .version(user.getProfileVersion() == null ? 0 : user.getProfileVersion())
                .build();
    }

    public static RecoveryInfoVO toRecoveryInfo(User user, AccountProperties properties) {
        if (user == null || user.getRecoveredAt() == null) {
            return null;
        }
        LocalDate available = cancelAvailableDate(user, properties);
        return RecoveryInfoVO.builder()
                .newPhoneMasked(Masks.phone(user.getPhone()))
                .recoveredAt(BeijingTime.format(user.getRecoveredAt()))
                .cancelAvailableDate(available == null ? null : BeijingTime.format(available))
                .build();
    }

    /** 找回后 7 天内的可注销日期；超过 7 天返回 null */
    public static LocalDate cancelAvailableDate(User user, AccountProperties properties) {
        if (user == null || user.getRecoveredAt() == null) {
            return null;
        }
        LocalDate available = user.getRecoveredAt().toLocalDate().plusDays(properties.getCancelRestrict().toDays());
        return available.isAfter(BeijingTime.today()) ? available : null;
    }

    public static boolean cancelRestricted(User user, AccountProperties properties) {
        return cancelAvailableDate(user, properties) != null;
    }

    /** 姓名首字（按 Unicode 码点，emoji 不会被截断） */
    public static String initial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end);
    }

    public static boolean isCancelled(User user) {
        return user != null && UserStatus.CANCELLED.name().equals(user.getStatus());
    }

    public static boolean isDisabled(User user) {
        return user != null && UserStatus.DISABLED.name().equals(user.getStatus());
    }

    public static boolean isNormal(User user) {
        return user != null && UserStatus.NORMAL.name().equals(user.getStatus());
    }
}
