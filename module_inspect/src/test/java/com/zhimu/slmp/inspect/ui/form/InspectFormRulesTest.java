package com.zhimu.slmp.inspect.ui.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * I02 / I03 表单校验：检查项必选、异常自动判定、手动标异常原因、说明字数、补录日期与执行人。
 */
public class InspectFormRulesTest {

    private static final String TEMPLATE = "{\"template_id\":11,\"template_version\":2,\"items\":["
            + "{\"item_id\":101,\"item_name\":\"精神状态\",\"sort_no\":1,\"options\":["
            + "{\"option_id\":1011,\"option_name\":\"正常\",\"is_abnormal\":false,\"sort_no\":1},"
            + "{\"option_id\":1012,\"option_name\":\"沉郁\",\"is_abnormal\":true,\"sort_no\":2}]},"
            + "{\"item_id\":102,\"item_name\":\"采食\",\"sort_no\":2,\"options\":["
            + "{\"option_id\":1021,\"option_name\":\"正常\",\"is_abnormal\":false,\"sort_no\":1},"
            + "{\"option_id\":1022,\"option_name\":\"减少\",\"is_abnormal\":true,\"sort_no\":2}]},"
            + "{\"item_id\":103,\"item_name\":\"粪便\",\"sort_no\":3,\"options\":["
            + "{\"option_id\":1031,\"option_name\":\"正常\",\"is_abnormal\":false,\"sort_no\":1},"
            + "{\"option_id\":1033,\"option_name\":\"水样\",\"is_abnormal\":true,\"sort_no\":3}]}]}";

    private List<InspectionFormVO.TemplateItem> items;

    @Before
    public void setUp() {
        items = GsonFactory.get().fromJson(TEMPLATE, InspectionFormVO.InspectTemplate.class).getItems();
    }

    private InspectFormRules.Input normalInput() {
        InspectFormRules.Input in = new InspectFormRules.Input();
        in.items = items;
        in.selections.put(101L, 1011L);
        in.selections.put(102L, 1021L);
        in.selections.put(103L, 1031L);
        return in;
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    @Test
    public void allItemsSelected_noError() {
        assertTrue(InspectFormRules.validate(normalInput()).isEmpty());
    }

    @Test
    public void missingItems_reportEachInPageOrder() {
        InspectFormRules.Input in = new InspectFormRules.Input();
        in.items = items;
        in.selections.put(102L, 1022L);
        Map<String, String> e = InspectFormRules.validate(in);
        assertEquals(2, e.size());
        List<String> keys = new ArrayList<>(e.keySet());
        assertEquals("item_101", keys.get(0));
        assertEquals("请选择精神状态", e.get("item_101"));
        assertEquals("请选择粪便", e.get("item_103"));
    }

    @Test
    public void optionNotInTemplate_isTreatedAsMissing() {
        InspectFormRules.Input in = normalInput();
        in.selections.put(103L, 9999L);
        assertEquals("请选择粪便", InspectFormRules.validate(in).get(InspectFormRules.itemKey(103)));
    }

    @Test
    public void abnormalOption_autoMarksAbnormalWithSummary() {
        Map<Long, Long> sel = normalInput().selections;
        assertFalse(InspectFormRules.checkAbnormal(items, sel));
        assertNull(InspectFormRules.abnormalSummary(items, sel));

        sel.put(103L, 1033L);
        assertTrue(InspectFormRules.checkAbnormal(items, sel));
        assertEquals("粪便为“水样”", InspectFormRules.abnormalSummary(items, sel));

        sel.put(102L, 1022L);
        assertEquals("采食为“减少”、粪便为“水样”", InspectFormRules.abnormalSummary(items, sel));
    }

    @Test
    public void manualMark_requiresReasonOnlyWhenChecksAreNormal() {
        InspectFormRules.Input in = normalInput();
        in.manualOn = true;
        in.manualReason = "   ";
        assertEquals(InspectFormRules.MSG_REASON_REQUIRED,
                InspectFormRules.validate(in).get(InspectFormRules.F_MANUAL_REASON));

        // 检查项已异常：原因可不填
        in.selections.put(103L, 1033L);
        assertTrue(InspectFormRules.validate(in).isEmpty());
    }

    @Test
    public void manualReason_limit100CodePoints() {
        InspectFormRules.Input in = normalInput();
        in.manualOn = true;
        in.manualReason = repeat("咳", 100);
        assertTrue(InspectFormRules.validate(in).isEmpty());
        // 表情按 1 个字计
        in.manualReason = repeat("🐷", 100);
        assertTrue(InspectFormRules.validate(in).isEmpty());
        in.manualReason = repeat("咳", 101);
        assertEquals(InspectFormRules.MSG_REASON_TOO_LONG,
                InspectFormRules.validate(in).get(InspectFormRules.F_MANUAL_REASON));
    }

    @Test
    public void manualOff_reasonIgnored() {
        InspectFormRules.Input in = normalInput();
        in.manualOn = false;
        in.manualReason = repeat("咳", 300);
        assertTrue(InspectFormRules.validate(in).isEmpty());
    }

    @Test
    public void remark_limit200() {
        InspectFormRules.Input in = normalInput();
        in.remark = repeat("说", 200);
        assertTrue(InspectFormRules.validate(in).isEmpty());
        in.remark = repeat("说", 201);
        assertEquals(InspectFormRules.MSG_REMARK_TOO_LONG, InspectFormRules.validate(in).get(InspectFormRules.F_REMARK));
    }

    @Test
    public void backfill_requiresDateAndExecutorFirst() {
        InspectFormRules.Input in = new InspectFormRules.Input();
        in.items = items;
        in.requireDate = true;
        in.requireExecutor = true;
        Map<String, String> e = InspectFormRules.validate(in);
        List<String> keys = new ArrayList<>(e.keySet());
        assertEquals(InspectFormRules.F_DATE, keys.get(0));
        assertEquals(InspectFormRules.F_EXECUTOR, keys.get(1));
        assertEquals(InspectFormRules.MSG_DATE_REQUIRED, e.get(InspectFormRules.F_DATE));
        assertEquals(InspectFormRules.MSG_EXECUTOR_REQUIRED, e.get(InspectFormRules.F_EXECUTOR));

        InspectFormRules.Input ok = normalInput();
        ok.requireDate = true;
        ok.requireExecutor = true;
        ok.inspectDate = "2026-10-18";
        ok.executorId = 10002L;
        assertTrue(InspectFormRules.validate(ok).isEmpty());
    }

    @Test
    public void banner_textsByCase() {
        assertNull(InspectFormRules.banner(null, false, false, null, null));
        assertTrue(InspectFormRules.banner("粪便为“水样”", false, false, null, null)
                .startsWith("粪便为“水样”，已自动标为异常。点“转上报”"));
        assertTrue(InspectFormRules.banner("粪便为“水样”", false, true, null, null).contains("补录记录不能转上报"));
        assertTrue(InspectFormRules.banner(null, true, false, "刘芳", "舍内氨味重").startsWith("刘芳已手动标为异常：舍内氨味重。"));
        assertTrue(InspectFormRules.banner(null, true, false, null, null).startsWith("已手动标为异常。"));
    }
}
