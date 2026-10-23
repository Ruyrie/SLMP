package com.zhimu.server.biz.farm.support;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.util.Masks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 模块02 表单一字段校验与文案（与 App FarmRules / 模块02 文档 2.5 一致） */
public final class FarmValidations {

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
    public static final String MSG_QUAL_TOO_MANY = "资质图最多上传 9 张";
    public static final String MSG_QUAL_DUPLICATE = "资质图不能重复";
    public static final String MSG_REMARK_TOO_LONG = "变更说明不能超过 100 个字";
    public static final String MSG_NO_CHANGE = "你还没有修改任何内容";
    public static final String MSG_VERSION_MISSING = "内容已更新，请刷新后再修改";

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern ADCODE = Pattern.compile("^\\d{6}$");
    private static final BigDecimal LNG_MIN = BigDecimal.valueOf(73);
    private static final BigDecimal LNG_MAX = BigDecimal.valueOf(136);
    private static final BigDecimal LAT_MIN = BigDecimal.valueOf(3);
    private static final BigDecimal LAT_MAX = BigDecimal.valueOf(54);

    private FarmValidations() {
    }

    /** 按 Unicode 码点计数（中文、emoji 都算 1 个字） */
    public static int length(String value) {
        if (value == null) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static BizException invalid(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static void requireVersion(Integer version) {
        if (version == null) {
            throw invalid("version", "内容已更新，请刷新后再修改");
        }
    }

    /**
     * 校验并规范化注册信息（F05/F07）：名称、品类、规模、联系人、手机号、地址与资质图。
     *
     * @return 规范化后的注册信息（名称、联系人、手机号去首尾空格；直辖市 city 为空时用省名补齐）
     */
    public static NormalizedRegistration normalizeRegistration(FarmRegistrationForm form,
                                                               BaseDataQueryService baseData) {
        String name = trim(form.getName());
        if (name.isEmpty()) {
            throw invalid("name", MSG_NAME_EMPTY);
        }
        if (length(name) > NAME_MAX) {
            throw invalid("name", MSG_NAME_TOO_LONG);
        }
        if (form.getMainCategoryId() == null) {
            throw invalid("main_category_id", MSG_CATEGORY_EMPTY);
        }
        CategoryInfo category = baseData.getCategory(form.getMainCategoryId());
        if (category == null || !category.enabled()) {
            throw invalid("main_category_id", MSG_CATEGORY_EMPTY);
        }
        Integer scale = form.getScale();
        if (scale == null) {
            throw invalid("scale", MSG_SCALE_EMPTY);
        }
        if (scale < SCALE_MIN || scale > SCALE_MAX) {
            throw invalid("scale", MSG_SCALE_INVALID);
        }
        String contactName = trim(form.getContactName());
        if (contactName.isEmpty()) {
            throw invalid("contact_name", MSG_CONTACT_EMPTY);
        }
        if (length(contactName) > CONTACT_MAX) {
            throw invalid("contact_name", MSG_CONTACT_TOO_LONG);
        }
        String contactPhone = normalizePhone(form.getContactPhone());
        Location location = normalizeLocation(form.getAddress(), form.getProvince(), form.getCity(),
                form.getDistrict(), form.getTownship(), form.getAdcode(), form.getLongitude(), form.getLatitude(),
                form.getLocAccuracy());
        List<Long> fileIds = normalizeFiles(form.getQualificationFileIds(), MSG_QUAL_EMPTY);
        return new NormalizedRegistration(name, category, scale, contactName, contactPhone, location.address(),
                location.province(), location.city(), location.district(), location.township(), location.adcode(),
                location.longitude(), location.latitude(), location.locAccuracy(), fileIds);
    }

    /** 校验资料变更的地址与资质图（F14；名称单独校验） */
    public static NormalizedChange normalizeChange(String name, String address, String province, String city,
                                                   String district, String township, String adcode, BigDecimal longitude,
                                                   BigDecimal latitude, Integer locAccuracy, List<Long> fileIds,
                                                   String remark) {
        String newName = trim(name);
        if (newName.isEmpty()) {
            throw invalid("name", MSG_NAME_EMPTY);
        }
        if (length(newName) > NAME_MAX) {
            throw invalid("name", MSG_NAME_TOO_LONG);
        }
        Location location = normalizeLocation(address, province, city, district, township, adcode, longitude,
                latitude, locAccuracy);
        List<Long> files = normalizeFiles(fileIds, MSG_QUAL_KEEP_ONE);
        String newRemark = trim(remark);
        if (length(newRemark) > REMARK_MAX) {
            throw invalid("remark", MSG_REMARK_TOO_LONG);
        }
        return new NormalizedChange(newName, location.address(), location.province(), location.city(),
                location.district(), location.township(), location.adcode(), location.longitude(),
                location.latitude(), location.locAccuracy(), files, newRemark.isEmpty() ? null : newRemark);
    }

    /** 手机号：去掉空格后必须 11 位、1 开头 */
    public static String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.replace(" ", "").trim();
        if (!PHONE.matcher(value).matches()) {
            throw invalid("contact_phone", MSG_PHONE_INVALID);
        }
        return value;
    }

    /** 邀请手机号：字段名 invitee_phone */
    public static String normalizeInviteePhone(String phone) {
        String value = phone == null ? "" : phone.replace(" ", "").trim();
        if (!PHONE.matcher(value).matches()) {
            throw invalid("invitee_phone", MSG_PHONE_INVALID);
        }
        return value;
    }

    public static void validateScale(Integer scale) {
        if (scale == null) {
            throw invalid("scale", MSG_SCALE_EMPTY);
        }
        if (scale < SCALE_MIN || scale > SCALE_MAX) {
            throw invalid("scale", MSG_SCALE_INVALID);
        }
    }

    public static void validateIntro(String intro) {
        if (length(intro) > INTRO_MAX) {
            throw invalid("intro", MSG_INTRO_TOO_LONG);
        }
    }

    public static void validateContactName(String contactName) {
        String value = trim(contactName);
        if (value.isEmpty()) {
            throw invalid("contact_name", MSG_CONTACT_EMPTY);
        }
        if (length(value) > CONTACT_MAX) {
            throw invalid("contact_name", MSG_CONTACT_TOO_LONG);
        }
    }

    public static String maskedPhone(String phone) {
        return Masks.phone(phone);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static Location normalizeLocation(String address, String province, String city, String district,
                                              String township, String adcode, BigDecimal longitude,
                                              BigDecimal latitude, Integer locAccuracy) {
        String fullAddress = trim(address);
        if (fullAddress.isEmpty() || length(fullAddress) > ADDRESS_MAX) {
            throw invalid("address", MSG_ADDRESS);
        }
        String p = trim(province);
        String c = trim(city);
        String d = trim(district);
        String t = trim(township);
        if (p.isEmpty() || length(p) > REGION_MAX || d.isEmpty() || length(d) > REGION_MAX
                || (!c.isEmpty() && length(c) > REGION_MAX) || (!t.isEmpty() && length(t) > REGION_MAX)) {
            throw invalid("province", MSG_ADDRESS);
        }
        if (c.isEmpty()) {
            c = p;
        }
        String code = trim(adcode);
        if (!ADCODE.matcher(code).matches()) {
            throw invalid("adcode", MSG_ADDRESS);
        }
        if (!inRange(longitude, LNG_MIN, LNG_MAX) || !inRange(latitude, LAT_MIN, LAT_MAX)) {
            throw invalid("longitude", MSG_ADDRESS);
        }
        if (locAccuracy != null && (locAccuracy < 0 || locAccuracy > 10_000)) {
            throw invalid("loc_accuracy", MSG_ADDRESS);
        }
        return new Location(fullAddress, p, c, d, t.isEmpty() ? null : t, code, longitude, latitude,
                locAccuracy);
    }

    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0
                && value.stripTrailingZeros().scale() <= 7;
    }

    private static List<Long> normalizeFiles(List<Long> fileIds, String emptyMessage) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw invalid("qualification_file_ids", emptyMessage);
        }
        if (fileIds.size() > QUAL_MAX) {
            throw invalid("qualification_file_ids", MSG_QUAL_TOO_MANY);
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : fileIds) {
            if (id == null) {
                throw invalid("qualification_file_ids", emptyMessage);
            }
            unique.add(id);
        }
        if (unique.size() != fileIds.size()) {
            throw invalid("qualification_file_ids", MSG_QUAL_DUPLICATE);
        }
        return new ArrayList<>(unique);
    }

    private record Location(String address, String province, String city, String district, String township,
                            String adcode, BigDecimal longitude, BigDecimal latitude, Integer locAccuracy) {
    }

    /** 规范化后的注册信息 */
    public record NormalizedRegistration(String name, CategoryInfo category, Integer scale, String contactName,
                                         String contactPhone, String address, String province, String city,
                                         String district, String township, String adcode, BigDecimal longitude,
                                         BigDecimal latitude, Integer locAccuracy, List<Long> qualificationFileIds) {
    }

    /** 规范化后的资料变更信息 */
    public record NormalizedChange(String name, String address, String province, String city, String district,
                                   String township, String adcode, BigDecimal longitude, BigDecimal latitude,
                                   Integer locAccuracy, List<Long> qualificationFileIds, String remark) {
    }
}
