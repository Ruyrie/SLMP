package com.zhimu.slmp.account;

import com.zhimu.slmp.network.GsonFactory;

/**
 * 测试用的示例数据（约定文档 4.4 节）：由 JSON 经项目 Gson（下划线命名）解析成 VO。
 */
public final class TestData {

    private TestData() {
    }

    public static <T> T fromJson(String json, Class<T> cls) {
        return GsonFactory.get().fromJson(json, cls);
    }

    public static final String TOKEN_OWNER = "{\"access_token\":\"mock-owner\",\"refresh_token\":\"r\",\"next_step\":\"NONE\","
            + "\"user\":{\"user_id\":10001,\"name\":\"王建国\",\"name_initial\":\"王\",\"phone_masked\":\"138****6620\"},"
            + "\"last_farm_id\":1001}";

    public static final String TOKEN_REGISTER = "{\"access_token\":\"mock-owner\",\"refresh_token\":\"r\","
            + "\"next_step\":\"SAVE_RECOVERY_CODE\",\"user\":{\"user_id\":10001,\"name\":\"王建国\"},"
            + "\"recovery_code\":{\"recovery_code_id\":501,\"code\":\"8KQ4T2MW7HXN3PDR\",\"scene\":\"REGISTER\","
            + "\"phone_masked\":\"138****6620\",\"is_regenerated\":false}}";

    public static final String AGREEMENT = "{\"user_agreement\":{\"version\":\"2026-10-01\",\"title\":\"用户协议\","
            + "\"url\":\"https://x/user\"},\"privacy_policy\":{\"version\":\"2026-10-01\",\"title\":\"隐私政策\","
            + "\"url\":\"https://x/privacy\"},\"agreement_version\":\"2026-10-01\",\"customer_service_phone\":\"400-123-4567\"}";

    public static final String SMS_SENT = "{\"phone_masked\":\"138****6620\",\"cooldown_seconds\":60,\"expire_seconds\":300}";

    public static final String FEED_FORM = "{\"biz_type\":\"FEED_RECORD\",\"biz_id\":7001,\"record_version\":\"3\","
            + "\"record_locator\":\"3 号育肥舍 · ZM2610-031 · 10月18日 晚餐\","
            + "\"fields\":[{\"field_code\":\"actual_qty\",\"field_name\":\"实际投喂量\",\"value\":\"140\","
            + "\"value_unit\":\"千克\",\"input_type\":\"NUMBER\",\"min\":0,\"max\":1000},"
            + "{\"field_code\":\"feed_id\",\"field_name\":\"饲料\",\"value\":\"育肥全价料\",\"input_type\":\"SELECT\"}],"
            + "\"void_allowed\":true,\"void_recalc_tags\":[\"库存（退回饲料）\",\"成本\",\"统计\"],"
            + "\"recalc_tags\":{\"actual_qty\":[\"头均采食量\",\"料肉比\",\"饲料成本\",\"经营报表\"]}}";

    public static final String FEED_FORM_PENDING = "{\"biz_type\":\"FEED_RECORD\",\"biz_id\":7001,\"record_version\":\"3\","
            + "\"fields\":[{\"field_code\":\"actual_qty\",\"field_name\":\"实际投喂量\",\"value\":\"140\","
            + "\"input_type\":\"NUMBER\"}],\"pending_request_id\":9008}";

    public static final String CHANGE_SUBMITTED = "{\"id\":9009,\"request_no\":\"CR2610190009\",\"status\":\"PENDING\"}";
}
