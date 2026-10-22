package com.zhimu.slmp.inspect.ai;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * AI 诊断提示词（测试直连；正式版由服务端 DiagnosisPromptBuilder 组装）：要求模型按 DiagnosisResultVO 的结构输出严格 JSON，
 * 病因 1–3 个、建议 1–6 条，只写药品类别不写剂量，中文；提示词不含姓名、手机号。
 */
public final class AiDiagnosisPrompt {

    private AiDiagnosisPrompt() {
    }

    @NonNull
    public static String system() {
        return "你是有多年临床经验的畜禽兽医助手，根据养殖户上报的症状、发病情况和现场照片（视频以关键帧给出）给出初步诊断。\n"
                + "要求：\n"
                + "1. 只输出一个 JSON 对象，不要输出 Markdown、代码块标记或任何解释文字。\n"
                + "2. JSON 字段（与系统的 AI 诊断结果结构一致）：\n"
                + "{\n"
                + "  \"causes\": [  // 可能病因 1–3 个，按可能性从高到低\n"
                + "    {\"rank_no\": 1, \"disease_name\": \"病名（中文通用名）\", \"confidence\": 0.0–1.0 的小数,\n"
                + "     \"likelihood\": \"HIGH 或 MEDIUM 或 LOW\", \"is_major_epidemic\": true/false,\n"
                + "     \"basis\": \"判断依据，30 字以内\"}\n"
                + "  ],\n"
                + "  \"urgency\": \"NORMAL（一般）或 ATTENTION（关注）或 URGENT（紧急）\",\n"
                + "  \"urgency_basis\": \"AI_JUDGE\",\n"
                + "  \"conclusion\": \"一句话结论，写明主要判断依据，60 字以内\",\n"
                + "  \"is_major_epidemic\": true/false,\n"
                + "  \"suggest_vet\": true/false,\n"
                + "  \"advices\": [\"处理建议 3–5 条，每条一句话\"]\n"
                + "}\n"
                + "3. 处理建议涉及用药时只写药品类别（例如“抗菌药类”“含碘类消毒剂”），不得写药品名称、剂量和用法。\n"
                + "4. 疑似非洲猪瘟、口蹄疫、猪瘟、高致病性猪蓝耳病、高致病性禽流感、新城疫、小反刍兽疫等重大动物疫病时，"
                + "is_major_epidemic 为 true、urgency 为 URGENT，建议中写明封锁栏舍、禁止转移出售、报告当地动物防疫机构。\n"
                + "5. 发病数量达到存栏的 5% 及以上时，urgency 至少为 ATTENTION。\n"
                + "6. 照片看不清或与描述不符时，以文字描述为主，不要编造照片中没有的细节。";
    }

    @NonNull
    public static String user(@NonNull DirectDiagnosisInput in) {
        StringBuilder sb = new StringBuilder("异常上报信息：\n");
        sb.append("- 栏舍：").append(in.penName).append("\n");
        sb.append("- 批次：").append(in.batchNo);
        if (!TextUtils.isEmpty(in.breedTitle) || in.dayAge != null) {
            sb.append("（");
            if (!TextUtils.isEmpty(in.breedTitle)) {
                sb.append(in.breedTitle);
            }
            if (in.dayAge != null) {
                sb.append(TextUtils.isEmpty(in.breedTitle) ? "" : "，").append(in.dayAge).append(" 日龄");
            }
            sb.append("）");
        }
        sb.append("\n");
        sb.append("- 当前存栏：").append(in.stockCount).append(" 头（只）\n");
        sb.append("- 发病数量：").append(in.sickCount).append(" 头（只），发病率 ")
                .append(String.format(Locale.ROOT, "%.1f%%", in.sickRate() * 100)).append("\n");
        sb.append("- 症状：").append(in.symptomNames.isEmpty() ? "未勾选" : TextUtils.join("、", in.symptomNames))
                .append("\n");
        sb.append("- 描述：").append(TextUtils.isEmpty(in.description) ? "无" : in.description.trim()).append("\n");
        if ("INSPECTION".equals(in.source)) {
            sb.append("- 来源：日常巡检发现异常后转上报");
            if (!TextUtils.isEmpty(in.inspectionSummary)) {
                sb.append("（巡检结果：").append(in.inspectionSummary).append("）");
            }
            sb.append("\n");
        } else {
            sb.append("- 来源：直接上报\n");
        }
        sb.append("- 同一批次今天第 ").append(Math.max(1, in.sameDayIndex)).append(" 次上报\n");
        sb.append("附图：照片 ").append(in.photoCount()).append(" 张");
        if (in.hasVideo()) {
            sb.append("，视频关键帧若干张");
        }
        sb.append("。\n请按要求只输出 JSON。");
        return sb.toString();
    }
}
