package com.zhimu.slmp.inspect.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zhimu.slmp.common.ai.AiJson;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.network.GsonFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 把模型回答整理为 {@link DiagnosisResultVO}（测试直连；规则与服务端 DiagnosisResultProcessor 一致，服务端文档 2.7 节）：
 * <ol>
 *     <li>取 JSON（容忍代码块标记、前后说明、尾逗号、截断），字段名兼容常见写法</li>
 *     <li>病因去重，按置信度（或可能性等级）取前 3；置信度 ≥ 0.60 较高、0.30–0.59 中等、&lt; 0.30 较低</li>
 *     <li>重大动物疫病按本地名录复核（模型标记不在名录中不算）</li>
 *     <li>建议删除含剂量的条目，最多 6 条</li>
 *     <li>紧急程度取最高：重大疫病 → 紧急；发病率 ≥ 5% 或同批次当天第 2 次上报 → 至少关注；其余取模型判定；
 *     依据按 MAJOR_EPIDEMIC &gt; SICK_RATE &gt; REPEAT_REPORT &gt; AI_JUDGE，结论句按依据生成</li>
 * </ol>
 * 取不到 JSON、没有病因或建议为 0 条时返回 null（视为直连失败，改走服务端接口）。
 */
public final class AiDiagnosisParser {

    /** 重大动物疫病名录（本地复核用，正式版以服务端知识库 is_major_epidemic 为准） */
    static final List<String> MAJOR_EPIDEMICS = Arrays.asList(
            "非洲猪瘟", "口蹄疫", "猪瘟", "高致病性猪蓝耳病", "高致病性禽流感", "禽流感", "新城疫", "小反刍兽疫",
            "牛结节性皮肤病", "牛瘟", "牛传染性胸膜肺炎", "痒病", "牛海绵状脑病", "绵羊痘", "山羊痘", "蓝舌病",
            "炭疽", "布鲁氏菌病", "猪水泡病", "非洲马瘟");

    public static final String EMERGENCY_NOTICE = "疑似重大动物疫病。请联系执业兽医并向当地动物防疫机构报告。";
    static final String FALLBACK_CONCLUSION = "暂未发现重大动物疫病特征，请结合现场情况继续观察。";
    static final int MAX_CAUSES = 3;
    static final int MAX_ADVICES = 6;
    static final int MAX_CONCLUSION = 100;

    /** 含剂量的建议：数字加 毫克、克、毫升、mg、g、ml、kg、IU 等 */
    private static final Pattern DOSAGE = Pattern.compile(
            "\\d+(\\.\\d+)?\\s*(mg|g|ml|mL|kg|μg|ug|iu|IU|毫克|微克|克|毫升|千克|公斤|单位|片|粒|支)(/|每|\\s|，|,|。|$)",
            Pattern.CASE_INSENSITIVE);

    private AiDiagnosisParser() {
    }

    /** 解析需要的上报信息 */
    public static final class Context {
        final int sickCount;
        final int stockCount;
        final int sameDayIndex;
        @NonNull
        final String generatedAt;
        @Nullable
        final String sourceLabel;

        public Context(int sickCount, int stockCount, int sameDayIndex, @NonNull String generatedAt,
                       @Nullable String sourceLabel) {
            this.sickCount = sickCount;
            this.stockCount = stockCount;
            this.sameDayIndex = sameDayIndex;
            this.generatedAt = generatedAt;
            this.sourceLabel = sourceLabel;
        }
    }

    private static final class Cause {
        String name;
        double confidence = -1;
        String likelihood;
        int rank = 99;
        boolean major;
    }

    @Nullable
    public static DiagnosisResultVO parse(@Nullable String content, @NonNull Context ctx) {
        JsonObject o = AiJson.extractObject(content);
        if (o == null) {
            return null;
        }
        List<Cause> causes = readCauses(o);
        if (causes.isEmpty()) {
            return null;
        }
        List<String> advices = readAdvices(o);
        if (advices.isEmpty()) {
            return null;
        }
        String modelUrgency = normalizeUrgency(str(o, "urgency", "urgency_level", "level"));
        boolean major = false;
        for (Cause c : causes) {
            major |= c.major;
        }
        double rate = ctx.stockCount <= 0 ? 0 : ctx.sickCount / (double) ctx.stockCount;
        String basis;
        String urgency;
        if (major) {
            urgency = DiagnosisResultVO.URGENCY_URGENT;
            basis = DiagnosisResultVO.BASIS_MAJOR_EPIDEMIC;
        } else {
            int modelLevel = level(modelUrgency);
            int ruleLevel = 0;
            String ruleBasis = null;
            if (rate >= 0.05 - 1e-9) {
                ruleLevel = 1;
                ruleBasis = DiagnosisResultVO.BASIS_SICK_RATE;
            } else if (ctx.sameDayIndex >= 2) {
                ruleLevel = 1;
                ruleBasis = DiagnosisResultVO.BASIS_REPEAT_REPORT;
            }
            // 模型判定为紧急但未命中重大疫病：取模型；同级时规则依据优先
            if (ruleBasis != null && ruleLevel >= modelLevel) {
                urgency = DiagnosisResultVO.URGENCY_ATTENTION;
                basis = ruleBasis;
            } else {
                urgency = modelUrgency;
                basis = DiagnosisResultVO.BASIS_AI_JUDGE;
            }
        }
        String conclusion = conclusion(basis, ctx, str(o, "conclusion", "summary", "diagnosis"));

        JsonObject out = new JsonObject();
        out.addProperty("diagnosis_id", 0);
        out.addProperty("generated_at", ctx.generatedAt);
        out.addProperty("conclusion", conclusion);
        out.addProperty("disclaimer", DiagnosisResultVO.DISCLAIMER);
        out.addProperty("urgency", urgency);
        out.addProperty("urgency_text", urgencyText(urgency));
        out.addProperty("urgency_basis", basis);
        out.addProperty("is_major_epidemic", major);
        Boolean vet = bool(o, "suggest_vet");
        out.addProperty("suggest_vet", vet == null || vet || major);
        if (major) {
            out.addProperty("emergency_notice", EMERGENCY_NOTICE);
        }
        JsonArray causeArr = new JsonArray();
        for (int i = 0; i < causes.size(); i++) {
            Cause c = causes.get(i);
            JsonObject co = new JsonObject();
            co.addProperty("rank_no", i + 1);
            co.addProperty("disease_name", c.name);
            co.addProperty("likelihood", c.likelihood);
            co.addProperty("likelihood_text", likelihoodText(c.likelihood));
            co.addProperty("is_major_epidemic", c.major);
            causeArr.add(co);
        }
        out.add("causes", causeArr);
        JsonArray adv = new JsonArray();
        for (String a : advices) {
            adv.add(a);
        }
        out.add("advices", adv);
        out.addProperty("source_label", ctx.sourceLabel);
        return GsonFactory.get().fromJson(out, DiagnosisResultVO.class);
    }

    // ===== 病因 =====

    @NonNull
    private static List<Cause> readCauses(@NonNull JsonObject o) {
        JsonArray arr = arr(o, "causes", "possible_causes", "possible_diseases", "diseases");
        Map<String, Cause> byName = new LinkedHashMap<>();
        if (arr == null) {
            return new ArrayList<>();
        }
        int index = 0;
        for (JsonElement e : arr) {
            index++;
            Cause c = new Cause();
            if (e.isJsonPrimitive()) {
                c.name = e.getAsString();
            } else if (e.isJsonObject()) {
                JsonObject co = e.getAsJsonObject();
                c.name = str(co, "disease_name", "name", "disease", "cause");
                c.confidence = normalizeConfidence(num(co, "confidence", "probability", "score"));
                c.likelihood = normalizeLikelihood(str(co, "likelihood", "likelihood_text", "possibility"));
                Double rank = num(co, "rank_no", "rank");
                c.rank = rank == null ? index : rank.intValue();
            } else {
                continue;
            }
            if (c.name == null) {
                continue;
            }
            c.name = c.name.trim();
            if (c.name.isEmpty()) {
                continue;
            }
            if (c.confidence >= 0) {
                c.likelihood = likelihoodOfConfidence(c.confidence);
            } else if (c.likelihood == null) {
                c.likelihood = DiagnosisResultVO.LIKELIHOOD_MEDIUM;
            }
            c.major = isMajorEpidemic(c.name);
            Cause old = byName.get(c.name);
            if (old == null || score(c) > score(old)) {
                byName.put(c.name, c);
            }
        }
        List<Cause> list = new ArrayList<>(byName.values());
        list.sort(Comparator.comparingDouble(AiDiagnosisParser::score).reversed()
                .thenComparingInt(c -> c.rank));
        return list.size() > MAX_CAUSES ? new ArrayList<>(list.subList(0, MAX_CAUSES)) : list;
    }

    /** 排序分值：有置信度用置信度，否则按等级 */
    private static double score(@NonNull Cause c) {
        if (c.confidence >= 0) {
            return c.confidence;
        }
        switch (c.likelihood) {
            case DiagnosisResultVO.LIKELIHOOD_HIGH:
                return 0.7;
            case DiagnosisResultVO.LIKELIHOOD_LOW:
                return 0.2;
            default:
                return 0.45;
        }
    }

    @VisibleForTesting
    static boolean isMajorEpidemic(@NonNull String name) {
        for (String m : MAJOR_EPIDEMICS) {
            if (name.contains(m)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    @NonNull
    static String likelihoodOfConfidence(double c) {
        if (c >= 0.60 - 1e-9) {
            return DiagnosisResultVO.LIKELIHOOD_HIGH;
        }
        if (c >= 0.30 - 1e-9) {
            return DiagnosisResultVO.LIKELIHOOD_MEDIUM;
        }
        return DiagnosisResultVO.LIKELIHOOD_LOW;
    }

    /** 置信度：0–1；写成 0–100 的按百分数换算 */
    private static double normalizeConfidence(@Nullable Double v) {
        if (v == null || v < 0) {
            return -1;
        }
        return v > 1 ? Math.min(1, v / 100.0) : v;
    }

    @Nullable
    private static String normalizeLikelihood(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("HIGH") || t.contains("较高") || t.equals("高")) {
            return DiagnosisResultVO.LIKELIHOOD_HIGH;
        }
        if (t.startsWith("LOW") || t.contains("较低") || t.equals("低")) {
            return DiagnosisResultVO.LIKELIHOOD_LOW;
        }
        if (t.startsWith("MEDIUM") || t.startsWith("MID") || t.contains("中")) {
            return DiagnosisResultVO.LIKELIHOOD_MEDIUM;
        }
        return null;
    }

    @NonNull
    static String likelihoodText(@NonNull String likelihood) {
        switch (likelihood) {
            case DiagnosisResultVO.LIKELIHOOD_HIGH:
                return "较高";
            case DiagnosisResultVO.LIKELIHOOD_LOW:
                return "较低";
            default:
                return "中等";
        }
    }

    // ===== 建议 =====

    @NonNull
    private static List<String> readAdvices(@NonNull JsonObject o) {
        JsonArray arr = arr(o, "advices", "advice", "suggestions", "treatment_advices", "recommendations");
        List<String> list = new ArrayList<>();
        if (arr == null) {
            String single = str(o, "advices", "advice", "suggestions");
            if (single != null) {
                for (String line : single.split("[\\n；;]")) {
                    addAdvice(list, line);
                }
            }
            return list;
        }
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) {
                addAdvice(list, e.getAsString());
            } else if (e.isJsonObject()) {
                addAdvice(list, str(e.getAsJsonObject(), "content", "text", "advice"));
            }
            if (list.size() >= MAX_ADVICES) {
                break;
            }
        }
        return list;
    }

    private static void addAdvice(@NonNull List<String> list, @Nullable String raw) {
        if (raw == null || list.size() >= MAX_ADVICES) {
            return;
        }
        // 去掉模型自带的“1.”“①”等序号，界面统一编号
        String a = raw.trim().replaceFirst("^(\\d+\\s*[.、．)）]|[①②③④⑤⑥⑦⑧⑨]|[-•·])\\s*", "").trim();
        if (a.isEmpty() || containsDosage(a)) {
            return;
        }
        list.add(a);
    }

    @VisibleForTesting
    static boolean containsDosage(@NonNull String advice) {
        return DOSAGE.matcher(advice).find();
    }

    // ===== 紧急程度与结论 =====

    @NonNull
    static String normalizeUrgency(@Nullable String s) {
        if (s == null) {
            return DiagnosisResultVO.URGENCY_NORMAL;
        }
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("URGENT") || t.contains("紧急")) {
            return DiagnosisResultVO.URGENCY_URGENT;
        }
        if (t.startsWith("ATTENTION") || t.contains("关注") || t.startsWith("WARN")) {
            return DiagnosisResultVO.URGENCY_ATTENTION;
        }
        return DiagnosisResultVO.URGENCY_NORMAL;
    }

    private static int level(@NonNull String urgency) {
        switch (urgency) {
            case DiagnosisResultVO.URGENCY_URGENT:
                return 2;
            case DiagnosisResultVO.URGENCY_ATTENTION:
                return 1;
            default:
                return 0;
        }
    }

    @NonNull
    static String urgencyText(@NonNull String urgency) {
        switch (urgency) {
            case DiagnosisResultVO.URGENCY_URGENT:
                return "紧急";
            case DiagnosisResultVO.URGENCY_ATTENTION:
                return "关注";
            default:
                return "一般";
        }
    }

    @NonNull
    static String conclusion(@NonNull String basis, @NonNull Context ctx, @Nullable String model) {
        switch (basis) {
            case DiagnosisResultVO.BASIS_MAJOR_EPIDEMIC:
                return "命中重大动物疫病，紧急程度定为“紧急”。";
            case DiagnosisResultVO.BASIS_SICK_RATE:
                return "发病 " + ctx.sickCount + " 头，达到存栏的 5% 以上，紧急程度至少为“关注”。";
            case DiagnosisResultVO.BASIS_REPEAT_REPORT:
                return "同一批次今天第 " + ctx.sameDayIndex + " 次上报，紧急程度至少为“关注”。";
            default:
                String m = model == null ? "" : model.trim();
                if (m.isEmpty()) {
                    return FALLBACK_CONCLUSION;
                }
                return m.length() > MAX_CONCLUSION ? m.substring(0, MAX_CONCLUSION) + "…" : m;
        }
    }

    // ===== JSON 读取 =====

    @Nullable
    private static String str(@NonNull JsonObject o, @NonNull String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) {
                String s = e.getAsString();
                if (!s.trim().isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Double num(@NonNull JsonObject o, @NonNull String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    String s = e.getAsString().trim().replace("%", "");
                    double v = Double.parseDouble(s);
                    if (e.getAsString().contains("%")) {
                        v = v / 100.0;
                    }
                    return v;
                } catch (NumberFormatException ignore) {
                    // 下一个键
                }
            }
        }
        return null;
    }

    @Nullable
    private static Boolean bool(@NonNull JsonObject o, @NonNull String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        String s = e.getAsString().trim();
        return "true".equalsIgnoreCase(s) || "是".equals(s) || "1".equals(s);
    }

    @Nullable
    private static JsonArray arr(@NonNull JsonObject o, @NonNull String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonArray()) {
                return e.getAsJsonArray();
            }
        }
        return null;
    }
}
