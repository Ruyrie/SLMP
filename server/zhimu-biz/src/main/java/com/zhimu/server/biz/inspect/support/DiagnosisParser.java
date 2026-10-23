package com.zhimu.server.biz.inspect.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 解析并校验模型输出：结构、病因去重、建议过滤剂量、紧急程度兜底。 */
public final class DiagnosisParser {

    private static final JsonMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();
    /** 含剂量的建议删除（数字 + 剂量单位）。 */
    private static final Pattern DOSAGE = Pattern.compile(
            "(?i)(\\d+(\\.\\d+)?\\s*(mg|g|ml|毫克|克|毫升))");
    /** 重大动物疫病名称（模型未标记时兜底）。 */
    private static final Set<String> MAJOR_DISEASES = Set.of(
            "非洲猪瘟", "口蹄疫", "高致病性禽流感", "小反刍兽疫", "蓝舌病", "疯牛病",
            "猪水泡病", "非洲马瘟", "牛结节性皮肤病", "山羊痘", "绵羊痘");

    private DiagnosisParser() {
    }

    public static ParsedDiagnosis parse(String content, int sickCount, BigDecimal sickRate, int sameDaySeq) {
        if (content == null || content.isBlank()) {
            throw new InvalidDiagnosisOutputException("模型没有返回内容");
        }
        JsonNode root;
        try {
            String json = extractJson(content);
            root = MAPPER.readTree(json);
        } catch (RuntimeException e) {
            throw new InvalidDiagnosisOutputException("模型输出不是有效 JSON");
        }
        List<ParsedDiagnosis.Cause> causes = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        JsonNode causeNodes = root.get("causes");
        if (causeNodes != null && causeNodes.isArray()) {
            int rank = 1;
            for (JsonNode node : causeNodes) {
                String name = text(node, "disease_name");
                if (name == null || name.isBlank() || seen.containsKey(name) || causes.size() >= 3) {
                    continue;
                }
                String likelihood = normalizeLikelihood(text(node, "likelihood"));
                BigDecimal confidence = decimal(node, "confidence");
                if (confidence == null) {
                    confidence = switch (likelihood) {
                        case "HIGH" -> new BigDecimal("0.7000");
                        case "MEDIUM" -> new BigDecimal("0.4000");
                        default -> new BigDecimal("0.2000");
                    };
                }
                boolean major = bool(node, "is_major_epidemic") || isMajorDisease(name);
                Long knowledgeId = node.has("knowledge_id") && !node.get("knowledge_id").isNull()
                        ? node.get("knowledge_id").asLong() : null;
                String reason = text(node, "reason");
                seen.put(name, major);
                causes.add(new ParsedDiagnosis.Cause(rank++, name, likelihood, confidence, major, knowledgeId,
                        reason));
            }
        }
        if (causes.isEmpty()) {
            throw new InvalidDiagnosisOutputException("模型没有给出有效病因");
        }
        List<String> advices = new ArrayList<>();
        JsonNode adviceNodes = root.get("advices");
        if (adviceNodes != null && adviceNodes.isArray()) {
            for (JsonNode node : adviceNodes) {
                String advice = node.isTextual() ? node.asString().trim() : "";
                if (advice.isEmpty() || DOSAGE.matcher(advice).find()) {
                    continue;
                }
                if (!advices.contains(advice)) {
                    advices.add(advice);
                }
                if (advices.size() >= 6) {
                    break;
                }
            }
        }
        if (advices.isEmpty()) {
            throw new InvalidDiagnosisOutputException("模型没有给出有效建议");
        }
        boolean major = seen.values().stream().anyMatch(Boolean::booleanValue);
        for (ParsedDiagnosis.Cause cause : causes) {
            major = major || cause.majorEpidemic();
        }
        String modelUrgency = normalizeUrgency(text(root, "urgency"));
        String basis;
        String urgency;
        if (major) {
            urgency = "URGENT";
            basis = "MAJOR_EPIDEMIC";
        } else if (sickRate != null && sickRate.compareTo(new BigDecimal("0.0500")) >= 0) {
            urgency = atLeastAttention(modelUrgency);
            basis = "SICK_RATE";
        } else if (sameDaySeq >= 2) {
            urgency = atLeastAttention(modelUrgency);
            basis = "REPEAT_REPORT";
        } else {
            urgency = modelUrgency;
            basis = "AI_JUDGE";
        }
        String conclusion = text(root, "conclusion");
        if ("MAJOR_EPIDEMIC".equals(basis)) {
            conclusion = "命中重大动物疫病，紧急程度定为“紧急”。";
        } else if ("SICK_RATE".equals(basis)) {
            conclusion = "发病 " + sickCount + " 头，达到存栏的 5% 以上，紧急程度至少为“关注”。";
        } else if ("REPEAT_REPORT".equals(basis)) {
            conclusion = "同一批次今天第 " + sameDaySeq + " 次上报，紧急程度至少为“关注”。";
        } else if (conclusion == null || conclusion.isBlank()) {
            conclusion = "模型判定紧急程度为“" + urgency + "”。";
        }
        if (conclusion.length() > 200) {
            conclusion = conclusion.substring(0, 200);
        }
        String emergency = text(root, "emergency_notice");
        boolean suggestVet = !root.has("suggest_vet") || bool(root, "suggest_vet");
        return new ParsedDiagnosis(conclusion, urgency, basis, major, suggestVet, emergency, causes, advices);
    }

    private static String extractJson(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            text = firstLine >= 0 ? text.substring(firstLine + 1) : text;
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidDiagnosisOutputException("模型输出缺少 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null
                : BigDecimal.valueOf(value.asDouble());
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static String normalizeUrgency(String urgency) {
        if (urgency == null) {
            return "NORMAL";
        }
        return switch (urgency.trim().toUpperCase(Locale.ROOT)) {
            case "URGENT" -> "URGENT";
            case "ATTENTION" -> "ATTENTION";
            default -> "NORMAL";
        };
    }

    private static String atLeastAttention(String urgency) {
        return "URGENT".equals(urgency) ? "URGENT" : "ATTENTION";
    }

    private static String normalizeLikelihood(String likelihood) {
        if (likelihood == null) {
            return "MEDIUM";
        }
        return switch (likelihood.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "HIGH";
            case "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    public static boolean isMajorDisease(String name) {
        if (name == null) {
            return false;
        }
        for (String disease : MAJOR_DISEASES) {
            if (name.contains(disease)) {
                return true;
            }
        }
        return false;
    }

    /** 模型输出无效（结构、病因或建议不满足要求）。 */
    public static class InvalidDiagnosisOutputException extends RuntimeException {
        public InvalidDiagnosisOutputException(String message) {
            super(message);
        }
    }
}
