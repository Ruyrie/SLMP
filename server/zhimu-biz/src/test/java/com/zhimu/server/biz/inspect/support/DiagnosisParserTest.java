package com.zhimu.server.biz.inspect.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisParserTest {

    @Test
    void parsesJsonAndFiltersDosageAndMarksMajorDisease() {
        String json = """
                ```json
                {
                  "conclusion": "疑似非洲猪瘟",
                  "urgency": "ATTENTION",
                  "causes": [
                    {"disease_name":"非洲猪瘟","likelihood":"HIGH","confidence":0.82,
                     "is_major_epidemic":false,"reason":"高热、皮肤发紫"},
                    {"disease_name":"猪丹毒","likelihood":"MEDIUM","confidence":0.41}
                  ],
                  "advices": ["立即隔离发病猪只", "使用某药 5mg 每头", "栏舍带猪消毒"],
                  "suggest_vet": true,
                  "emergency_notice": "请联系防疫机构"
                }
                ```
                """;

        ParsedDiagnosis parsed = DiagnosisParser.parse(json, 12, new BigDecimal("0.0600"), 1);

        assertThat(parsed.majorEpidemic()).isTrue();
        assertThat(parsed.urgency()).isEqualTo("URGENT");
        assertThat(parsed.urgencyBasis()).isEqualTo("MAJOR_EPIDEMIC");
        assertThat(parsed.causes()).hasSize(2);
        assertThat(parsed.causes().get(0).likelihood()).isEqualTo("HIGH");
        assertThat(parsed.advices()).containsExactly("立即隔离发病猪只", "栏舍带猪消毒");
        assertThat(parsed.conclusion()).contains("重大动物疫病");
    }

    @Test
    void sickRateAtFivePercentRaisesAttention() {
        String json = """
                {"conclusion":"普通腹泻","urgency":"NORMAL",
                 "causes":[{"disease_name":"猪流行性腹泻","likelihood":"HIGH","confidence":0.72}],
                 "advices":["隔离","补液"],"suggest_vet":true}
                """;

        ParsedDiagnosis parsed = DiagnosisParser.parse(json, 12, new BigDecimal("0.0500"), 1);

        assertThat(parsed.urgency()).isEqualTo("ATTENTION");
        assertThat(parsed.urgencyBasis()).isEqualTo("SICK_RATE");
        assertThat(parsed.conclusion()).contains("发病 12 头").contains("5%");
    }

    @Test
    void repeatReportSameDayRaisesAttention() {
        String json = """
                {"conclusion":"复诊","urgency":"NORMAL",
                 "causes":[{"disease_name":"大肠杆菌病","likelihood":"MEDIUM","confidence":0.35}],
                 "advices":["观察"],"suggest_vet":false}
                """;

        ParsedDiagnosis parsed = DiagnosisParser.parse(json, 5, new BigDecimal("0.0250"), 2);

        assertThat(parsed.urgency()).isEqualTo("ATTENTION");
        assertThat(parsed.urgencyBasis()).isEqualTo("REPEAT_REPORT");
        assertThat(parsed.suggestVet()).isFalse();
    }

    @Test
    void invalidOutputThrows() {
        assertThatThrownBy(() -> DiagnosisParser.parse("不是 JSON", 1, new BigDecimal("0.0100"), 1))
                .isInstanceOf(DiagnosisParser.InvalidDiagnosisOutputException.class);

        assertThatThrownBy(() -> DiagnosisParser.parse("""
                {"causes":[],"advices":["隔离"]}
                """, 1, new BigDecimal("0.0100"), 1))
                .isInstanceOf(DiagnosisParser.InvalidDiagnosisOutputException.class);

        assertThatThrownBy(() -> DiagnosisParser.parse("""
                {"causes":[{"disease_name":"腹泻","likelihood":"HIGH"}],"advices":[]}
                """, 1, new BigDecimal("0.0100"), 1))
                .isInstanceOf(DiagnosisParser.InvalidDiagnosisOutputException.class);
    }
}
