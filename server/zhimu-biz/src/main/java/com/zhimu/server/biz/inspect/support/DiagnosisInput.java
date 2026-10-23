package com.zhimu.server.biz.inspect.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 组装诊断提示词所需的数据（不含姓名、手机号等个人信息）。 */
public record DiagnosisInput(long reportId, String farmName, String penName, String batchNo, String breedName,
                             String categoryName, Integer dayAge, Integer stockCount, LocalDate entryDate,
                             Integer entryAgeDays, String source, List<String> symptoms, int sickCount,
                             BigDecimal sickRate, String description, int sameDaySeq,
                             List<String> inspectionItems, String manualReason) {
}
