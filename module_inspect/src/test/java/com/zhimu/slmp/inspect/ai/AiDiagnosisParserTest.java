package com.zhimu.slmp.inspect.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;

import org.junit.Test;

import java.util.List;

/**
 * 测试直连的 AI 回答解析：取 JSON、病因前 3、可能性等级、重大疫病、剂量过滤、紧急程度下限、格式错误兜底。
 */
public class AiDiagnosisParserTest {

    private static final String TIME = "2026-10-19 09:21:00";
    private static final String LABEL = "测试直连 · 通义千问 qwen3.8-max";

    private static AiDiagnosisParser.Context ctx(int sick, int stock, int sameDayIndex) {
        return new AiDiagnosisParser.Context(sick, stock, sameDayIndex, TIME, LABEL);
    }

    private static final String NORMAL_JSON = "{\"urgency\":\"NORMAL\",\"conclusion\":\"疑似仔猪白痢，暂未见重大疫病特征。\","
            + "\"suggest_vet\":false,"
            + "\"causes\":[{\"disease_name\":\"仔猪白痢\",\"confidence\":0.72},"
            + "{\"disease_name\":\"饲养管理不当\",\"confidence\":0.35}],"
            + "\"advices\":[\"1. 保持栏舍干燥保温\",\"发病仔猪单独护理，保证饮水\"]}";

    @Test
    public void fencedJsonWithExtraText_parsed() {
        String content = "根据描述分析如下：\n```json\n" + NORMAL_JSON + "\n```\n以上仅供参考。";
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(2, 320, 1));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_NORMAL, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_AI_JUDGE, r.getUrgencyBasis());
        assertEquals("一般", r.getUrgencyText());
        assertEquals("疑似仔猪白痢，暂未见重大疫病特征。", r.getConclusion());
        assertEquals(DiagnosisResultVO.DISCLAIMER, r.getDisclaimer());
        assertEquals(TIME, r.getGeneratedAt());
        assertEquals(LABEL, r.getSourceLabel());
        assertFalse(r.isMajorEpidemic());
        assertFalse(r.isSuggestVet());
        assertNull(r.getEmergencyNotice());
        assertEquals(2, r.getCauses().size());
        assertEquals(1, r.getCauses().get(0).getRankNo());
        assertEquals("仔猪白痢", r.getCauses().get(0).getDiseaseName());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_HIGH, r.getCauses().get(0).getLikelihood());
        assertEquals("较高", r.getCauses().get(0).getLikelihoodText());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_MEDIUM, r.getCauses().get(1).getLikelihood());
        // 序号被去掉，由界面统一编号
        assertEquals("保持栏舍干燥保温", r.getAdvices().get(0));
    }

    @Test
    public void moreThanThreeCauses_dedupedAndTop3ByConfidence() {
        String content = "{\"causes\":["
                + "{\"name\":\"饲料霉变\",\"confidence\":0.1},"
                + "{\"name\":\"传染性胃肠炎\",\"confidence\":0.45},"
                + "{\"name\":\"猪流行性腹泻\",\"confidence\":0.5},"
                + "{\"name\":\"猪流行性腹泻\",\"confidence\":80},"
                + "{\"name\":\"大肠杆菌病\",\"confidence\":\"20%\"}],"
                + "\"advices\":[\"隔离发病猪只\"]}";
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(2, 320, 1));
        assertNotNull(r);
        List<DiagnosisResultVO.DiagnosisCause> causes = r.getCauses();
        assertEquals(3, causes.size());
        assertEquals("猪流行性腹泻", causes.get(0).getDiseaseName());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_HIGH, causes.get(0).getLikelihood());
        assertEquals("传染性胃肠炎", causes.get(1).getDiseaseName());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_MEDIUM, causes.get(1).getLikelihood());
        assertEquals("大肠杆菌病", causes.get(2).getDiseaseName());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_LOW, causes.get(2).getLikelihood());
        assertEquals(3, causes.get(2).getRankNo());
    }

    @Test
    public void chineseFieldValues_andStringCauses() {
        String content = "{\"urgency\":\"关注\",\"possible_causes\":[{\"disease\":\"猪丹毒\",\"likelihood\":\"较高\"},"
                + "\"副猪嗜血杆菌病\"],\"suggestions\":\"加强消毒；隔离观察\"}";
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(1, 320, 1));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_ATTENTION, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_AI_JUDGE, r.getUrgencyBasis());
        assertEquals("猪丹毒", r.getCauses().get(0).getDiseaseName());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_HIGH, r.getCauses().get(0).getLikelihood());
        assertEquals(DiagnosisResultVO.LIKELIHOOD_MEDIUM, r.getCauses().get(1).getLikelihood());
        assertEquals(2, r.getAdvices().size());
        assertTrue(r.isSuggestVet());
    }

    @Test
    public void dosageAdvicesRemoved() {
        String content = "{\"causes\":[{\"name\":\"猪流行性腹泻\",\"confidence\":0.7}],\"advices\":["
                + "\"隔离发病猪只\",\"每头肌注 2 毫升\",\"按 10mg/kg 体重口服\",\"饮水中加口服补液盐类\"]}";
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(2, 320, 1));
        assertNotNull(r);
        assertEquals(2, r.getAdvices().size());
        assertEquals("隔离发病猪只", r.getAdvices().get(0));
        assertEquals("饮水中加口服补液盐类", r.getAdvices().get(1));
        assertTrue(AiDiagnosisParser.containsDosage("每次 0.5 克"));
        assertFalse(AiDiagnosisParser.containsDosage("隔离 7 天后再混群"));
    }

    @Test
    public void majorEpidemic_forcesUrgentWithNotice() {
        String content = "{\"urgency\":\"NORMAL\",\"causes\":[{\"name\":\"猪丹毒\",\"confidence\":0.5},"
                + "{\"name\":\"非洲猪瘟\",\"confidence\":0.4}],\"advices\":[\"立即封锁栏舍\"],\"suggest_vet\":false}";
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(1, 320, 1));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_URGENT, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_MAJOR_EPIDEMIC, r.getUrgencyBasis());
        assertTrue(r.isMajorEpidemic());
        assertTrue(r.isSuggestVet());
        assertEquals(AiDiagnosisParser.EMERGENCY_NOTICE, r.getEmergencyNotice());
        assertEquals("命中重大动物疫病，紧急程度定为“紧急”。", r.getConclusion());
        assertTrue(r.getCauses().get(1).isMajorEpidemic());
        assertFalse(r.getCauses().get(0).isMajorEpidemic());
    }

    @Test
    public void sickRateAtLeast5Percent_raisesToAttention() {
        DiagnosisResultVO r = AiDiagnosisParser.parse(NORMAL_JSON, ctx(10, 200, 1));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_ATTENTION, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_SICK_RATE, r.getUrgencyBasis());
        assertEquals("发病 10 头，达到存栏的 5% 以上，紧急程度至少为“关注”。", r.getConclusion());

        DiagnosisResultVO below = AiDiagnosisParser.parse(NORMAL_JSON, ctx(9, 200, 1));
        assertNotNull(below);
        assertEquals(DiagnosisResultVO.URGENCY_NORMAL, below.getUrgency());
    }

    @Test
    public void repeatReportSameDay_raisesToAttention() {
        DiagnosisResultVO r = AiDiagnosisParser.parse(NORMAL_JSON, ctx(1, 320, 2));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_ATTENTION, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_REPEAT_REPORT, r.getUrgencyBasis());
        assertEquals("同一批次今天第 2 次上报，紧急程度至少为“关注”。", r.getConclusion());
    }

    @Test
    public void modelUrgentWithoutMajor_keepsModelLevel() {
        String content = NORMAL_JSON.replace("\"urgency\":\"NORMAL\"", "\"urgency\":\"URGENT\"");
        DiagnosisResultVO r = AiDiagnosisParser.parse(content, ctx(12, 200, 1));
        assertNotNull(r);
        assertEquals(DiagnosisResultVO.URGENCY_URGENT, r.getUrgency());
        assertEquals(DiagnosisResultVO.BASIS_AI_JUDGE, r.getUrgencyBasis());
    }

    @Test
    public void malformedAnswers_returnNull() {
        assertNull(AiDiagnosisParser.parse(null, ctx(1, 100, 1)));
        assertNull(AiDiagnosisParser.parse("抱歉，根据现有信息无法判断。", ctx(1, 100, 1)));
        assertNull(AiDiagnosisParser.parse("```json\n{\"causes\": }\n```", ctx(1, 100, 1)));
        assertNull(AiDiagnosisParser.parse("[\"猪瘟\"]", ctx(1, 100, 1)));
        // 没有病因
        assertNull(AiDiagnosisParser.parse("{\"causes\":[],\"advices\":[\"隔离\"]}", ctx(1, 100, 1)));
        // 建议全是剂量
        assertNull(AiDiagnosisParser.parse("{\"causes\":[\"猪丹毒\"],\"advices\":[\"每头 2 毫升\"]}", ctx(1, 100, 1)));
    }

    @Test
    public void truncatedAnswer_repaired() {
        String cutInString = "{\"urgency\":\"NORMAL\",\"causes\":[{\"name\":\"猪流行性腹泻\",\"confidence\":0.7}],"
                + "\"advices\":[\"隔离发病猪只\",\"加强消毒\"],\"conclusion\":\"疑似病毒性腹";
        DiagnosisResultVO r = AiDiagnosisParser.parse(cutInString, ctx(1, 320, 1));
        assertNotNull(r);
        assertEquals("疑似病毒性腹", r.getConclusion());
        assertEquals(2, r.getAdvices().size());

        String cutAfterComma = "```json\n{\"causes\":[{\"name\":\"猪流行性腹泻\",\"confidence\":0.7}],"
                + "\"advices\":[\"隔离发病猪只\",";
        DiagnosisResultVO r2 = AiDiagnosisParser.parse(cutAfterComma, ctx(1, 320, 1));
        assertNotNull(r2);
        assertEquals(1, r2.getAdvices().size());

        String cutAfterKey = "{\"causes\":[{\"name\":\"猪流行性腹泻\",\"confidence\":0.7}],"
                + "\"advices\":[\"隔离发病猪只\"],\"conclusion\":";
        DiagnosisResultVO r3 = AiDiagnosisParser.parse(cutAfterKey, ctx(1, 320, 1));
        assertNotNull(r3);
        assertEquals("暂未发现重大动物疫病特征，请结合现场情况继续观察。", r3.getConclusion());
    }

    @Test
    public void helpers() {
        assertTrue(AiDiagnosisParser.isMajorEpidemic("疑似非洲猪瘟"));
        assertTrue(AiDiagnosisParser.isMajorEpidemic("口蹄疫"));
        assertFalse(AiDiagnosisParser.isMajorEpidemic("猪丹毒"));
        assertEquals(DiagnosisResultVO.LIKELIHOOD_HIGH, AiDiagnosisParser.likelihoodOfConfidence(0.6));
        assertEquals(DiagnosisResultVO.LIKELIHOOD_MEDIUM, AiDiagnosisParser.likelihoodOfConfidence(0.3));
        assertEquals(DiagnosisResultVO.LIKELIHOOD_LOW, AiDiagnosisParser.likelihoodOfConfidence(0.29));
        assertEquals(DiagnosisResultVO.URGENCY_NORMAL, AiDiagnosisParser.normalizeUrgency(null));
        assertEquals(DiagnosisResultVO.URGENCY_ATTENTION, AiDiagnosisParser.normalizeUrgency("attention"));
        assertEquals(DiagnosisResultVO.URGENCY_URGENT, AiDiagnosisParser.normalizeUrgency("紧急"));
    }
}
