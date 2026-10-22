package com.zhimu.slmp.farm.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.map.model.vo.LocationResult;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 模块02 表单规则与文案（与服务端模块02 文档 2.5.4–2.5.6 节一致）：名称、规模、联系人、手机号、地址。
 */
public final class FarmRules {

    private FarmRules() {
    }

    public static final int NAME_MAX = 20;
    public static final int CONTACT_MAX = 20;
    public static final int SCALE_MIN = 1;
    public static final int SCALE_MAX = 999_999;
    public static final int INTRO_MAX = 200;
    public static final int ADDRESS_MAX = 200;
    public static final int REGION_MAX = 30;
    public static final int REMARK_MAX = 100;
    public static final int QUAL_MAX = 9;

    public static final String MSG_NAME_EMPTY = "请填写养殖场名称";
    public static final String MSG_NAME_TOO_LONG = "养殖场名称不能超过 20 个字";
    public static final String MSG_NAME_DUP = "你已经有一个叫“%s”的养殖场，请换一个名称";
    public static final String MSG_ADDRESS = "请在地图上选择养殖场位置";
    public static final String MSG_SCALE_EMPTY = "请填写规模";
    public static final String MSG_SCALE_INVALID = "规模需为 1–999,999 之间的整数";
    public static final String MSG_CATEGORY_EMPTY = "请选择主要品类";
    public static final String MSG_CONTACT_EMPTY = "请填写联系人";
    public static final String MSG_CONTACT_TOO_LONG = "联系人不能超过 20 个字";
    public static final String MSG_PHONE_INVALID = "请输入 11 位手机号";
    public static final String MSG_INTRO_TOO_LONG = "简介不能超过 200 个字";
    public static final String MSG_QUAL_EMPTY = "请至少上传 1 张资质图";
    public static final String MSG_QUAL_KEEP_ONE = "请至少保留 1 张资质图";
    public static final String MSG_REMARK_TOO_LONG = "变更说明不能超过 100 个字";
    public static final String MSG_QUAL_UPLOADING = "资质图还在上传，请稍候";
    public static final String MSG_NETWORK_KEPT = "网络连接失败，已保留填写的内容，请联网后重新提交";
    public static final String MSG_SUBMIT_UNFINISHED = "提交没有完成，请重试";
    public static final String MSG_NO_CHANGE = "你还没有修改任何内容";

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern ADCODE = Pattern.compile("^\\d{6}$");
    private static final BigDecimal LNG_MIN = BigDecimal.valueOf(73);
    private static final BigDecimal LNG_MAX = BigDecimal.valueOf(136);
    private static final BigDecimal LAT_MIN = BigDecimal.valueOf(3);
    private static final BigDecimal LAT_MAX = BigDecimal.valueOf(54);

    /** 去首尾空格 */
    @NonNull
    public static String trim(@Nullable CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }

    public static int length(@Nullable CharSequence s) {
        String t = s == null ? "" : s.toString();
        return t.codePointCount(0, t.length());
    }

    /** 11 位数字、1 开头（输入框中的空格先去掉） */
    public static boolean isPhone(@Nullable CharSequence value) {
        return PHONE.matcher(PhoneTextWatcher.digits(value)).matches()
                && PhoneTextWatcher.digits(value).length() == trim(value).replace(" ", "").length();
    }

    /** 1–999999 的整数；不合格返回 null */
    @Nullable
    public static Integer parseScale(@Nullable CharSequence value) {
        String t = trim(value).replace(",", "");
        if (t.isEmpty() || !t.matches("\\d{1,7}")) {
            return null;
        }
        int v = Integer.parseInt(t);
        return v >= SCALE_MIN && v <= SCALE_MAX ? v : null;
    }

    /**
     * 地址可提交：地址 ≤200 字、省 / 区县必填且各 ≤30 字；地图选点时还要求 6 位 adcode 与国内经纬度范围。
     * 手动填写（source = MANUAL，未配置高德 Key）时经纬度与 adcode 可为空（待服务端确认按地址补齐）。
     */
    public static boolean isLocationValid(@Nullable LocationResult loc) {
        if (loc == null) {
            return false;
        }
        String address = trim(loc.getAddress());
        if (address.isEmpty() || length(address) > ADDRESS_MAX) {
            return false;
        }
        if (!region(loc.getProvince(), true) || !region(loc.getCity(), true) || !region(loc.getDistrict(), true)
                || !region(loc.getTownship(), false)) {
            return false;
        }
        if (LocationResult.SOURCE_MANUAL.equals(loc.getSource()) && !loc.hasCoordinate()) {
            return loc.getAdcode() == null || ADCODE.matcher(loc.getAdcode()).matches();
        }
        return loc.getAdcode() != null && ADCODE.matcher(loc.getAdcode()).matches()
                && inRange(loc.getLongitude(), LNG_MIN, LNG_MAX) && inRange(loc.getLatitude(), LAT_MIN, LAT_MAX);
    }

    private static boolean region(@Nullable String value, boolean required) {
        String t = trim(value);
        if (t.isEmpty()) {
            return !required;
        }
        return length(t) <= REGION_MAX;
    }

    private static boolean inRange(@Nullable BigDecimal v, @NonNull BigDecimal min, @NonNull BigDecimal max) {
        return v != null && v.compareTo(min) >= 0 && v.compareTo(max) <= 0 && v.stripTrailingZeros().scale() <= 7;
    }
}
