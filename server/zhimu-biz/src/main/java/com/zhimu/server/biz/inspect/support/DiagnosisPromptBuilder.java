package com.zhimu.server.biz.inspect.support;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 异常诊断提示词（DIAG_V1）：要求模型输出 JSON，只写药品类别不写剂量。 */
public final class DiagnosisPromptBuilder {

    public static final String PROMPT_VERSION = "DIAG_V1";

    private DiagnosisPromptBuilder() {
    }

    public static String systemPrompt() {
        return """
                你是智牧通养殖场的兽医辅助诊断助手。请根据提供的养殖场、批次、症状、巡检结果和照片，给出可能病因与处理建议。
                只输出一个 JSON 对象，不要输出 Markdown 代码块或解释文字。JSON 结构：
                {
                  "conclusion": "不超过 100 字的结论",
                  "urgency": "NORMAL|ATTENTION|URGENT",
                  "causes": [
                    {"disease_name": "病名", "likelihood": "HIGH|MEDIUM|LOW", "confidence": 0.0,
                     "is_major_epidemic": false, "reason": "判断依据"}
                  ],
                  "advices": ["处理建议，只写药品类别，不写剂量"],
                  "suggest_vet": true,
                  "emergency_notice": "命中重大动物疫病时的提示，可为空"
                }
                要求：
                1. causes 1–3 个，按可能性从高到低；advices 1–6 条。
                2. 建议中不要出现任何剂量（如 mg、g、ml、毫克、克、毫升）。
                3. 只有确诊或高度怀疑《动物防疫法》重大动物疫病时才把 is_major_epidemic 设为 true。
                4. 不要输出养殖户姓名、手机号等个人信息。
                """;
    }

    public static String userPrompt(DiagnosisInput input) {
        StringBuilder text = new StringBuilder(1024);
        text.append("养殖场：").append(nullValue(input.farmName())).append('\n');
        text.append("栏舍：").append(nullValue(input.penName())).append('\n');
        text.append("批次：").append(nullValue(input.batchNo()))
                .append("；品种：").append(nullValue(input.breedName()))
                .append("；品类：").append(nullValue(input.categoryName())).append('\n');
        text.append("日龄：").append(input.dayAge() == null ? "未知" : input.dayAge() + " 天")
                .append("；当前在栏：").append(input.stockCount() == null ? "未知" : input.stockCount() + " 头")
                .append('\n');
        if (input.entryDate() != null) {
            text.append("入栏日期：").append(input.entryDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append("；入栏日龄：").append(input.entryAgeDays() == null ? "未知" : input.entryAgeDays())
                    .append(" 天\n");
        }
        text.append("上报来源：").append("INSPECTION".equals(input.source()) ? "巡检转上报" : "直接上报").append('\n');
        text.append("发病数量：").append(input.sickCount()).append(" 头；发病率：")
                .append(input.sickRate() == null ? "未知" : input.sickRate().toPlainString()).append('\n');
        text.append("同日同批次第 ").append(input.sameDaySeq()).append(" 次上报\n");
        text.append("症状：").append(input.symptoms() == null || input.symptoms().isEmpty() ? "未勾选"
                : String.join("、", input.symptoms())).append('\n');
        if (input.description() != null && !input.description().isBlank()) {
            text.append("描述：").append(input.description()).append('\n');
        }
        if (input.inspectionItems() != null && !input.inspectionItems().isEmpty()) {
            text.append("巡检结果：").append(String.join("；", input.inspectionItems())).append('\n');
        }
        if (input.manualReason() != null && !input.manualReason().isBlank()) {
            text.append("手动标记原因：").append(input.manualReason()).append('\n');
        }
        text.append("请结合照片判断，并按要求输出 JSON。");
        return text.toString();
    }

    private static String nullValue(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    /** 图片数量上限（网关与提示词都限制）。 */
    public static List<String> limitImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.size() <= 4 ? images : images.subList(0, 4);
    }
}
