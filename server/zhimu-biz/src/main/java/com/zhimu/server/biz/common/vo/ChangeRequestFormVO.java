package com.zhimu.server.biz.common.vo;

import com.zhimu.server.common.vo.OptionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * U01 提交修改申请页的表单上下文（API-U01，App lib_common ChangeRequestFormVO）。
 * <p>
 * record_title / record_subtitle 在顶层；record_card 为 [{label, value}]；recalc_tags、field_hints 为顶层 map（键为 field_code）；
 * record_version、pending_request_id、void_allowed 必须返回。input_type 只用 NUMBER / TEXT / DATE / SELECT，
 * DATE 不下发 min / max，照片字段不下发。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequestFormVO {

    private String bizType;
    private Long bizId;
    /** UI 长名，例如 投喂记录、入库单 */
    private String bizTypeText;
    private String recordLocator;
    private String recordTitle;
    private String recordSubtitle;
    /** 提交 U02 时带回原样 */
    private String recordVersion;
    @Builder.Default
    private List<CardItem> recordCard = new ArrayList<>();
    @Builder.Default
    private List<Field> fields = new ArrayList<>();
    @Builder.Default
    private List<String> linkedRules = new ArrayList<>();
    private String reviewTip;
    private Long pendingRequestId;
    private boolean voidAllowed;
    private String voidNotice;
    @Builder.Default
    private List<String> voidRecalcTags = new ArrayList<>();
    /** field_code -> 重新计算范围 */
    @Builder.Default
    private Map<String, List<String>> recalcTags = new LinkedHashMap<>();
    /** field_code -> 新值参考或 SELECT 可选项 */
    @Builder.Default
    private Map<String, String> fieldHints = new LinkedHashMap<>();

    /** 原记录卡的一行 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardItem {
        private String label;
        private String value;
    }

    /** 可改字段：value 为纯值，单位单独放 valueUnit */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Field {
        private String fieldCode;
        private String fieldName;
        private String value;
        private String valueUnit;
        private String inputType;
        @Builder.Default
        private List<OptionVO> options = new ArrayList<>();
        private BigDecimal min;
        private BigDecimal max;
    }
}
