package com.zhimu.slmp.feed.ui.growth;

import com.zhimu.slmp.feed.TestRules;
import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;
import com.zhimu.slmp.feed.widget.FeedStatCard;
import com.zhimu.slmp.network.GsonFactory;
import org.junit.Rule;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GrowthMetricCardTest {
    @Rule public TestRules rules = new TestRules();

    @Test public void missingSnapshotComparisonIsLabeledAndRetainsSmallWeight() {
        GrowthCurveVO curve = GsonFactory.get().fromJson("{\"std_available\":true,\"metrics\":{"
                + "\"avg_weight\":0.05,\"std_weight\":0.04,\"deviation_ratio\":0.25,"
                + "\"allowed_deviation\":0.1,\"comparison_source\":\"CURRENT_STANDARD\"}}", GrowthCurveVO.class);
        List<FeedStatCard> cards = GrowthCurveViewModel.metricCardsOf(curve);
        assertEquals("0.05", cards.get(0).value);
        assertTrue(cards.get(1).helper.contains("0.04"));
        assertTrue(cards.get(1).helper.contains("按当前标准对比"));
        assertTrue(cards.get(1).helperError);
    }

    @Test public void existingSnapshotStillDisplaysWhenCurrentStandardIsUnavailable() {
        GrowthCurveVO curve = GsonFactory.get().fromJson("{\"std_available\":false,\"metrics\":{"
                + "\"avg_weight\":60,\"std_weight\":65,\"deviation_ratio\":-0.0769,"
                + "\"allowed_deviation\":0.1,\"comparison_source\":\"RECORDED\"}}", GrowthCurveVO.class);
        FeedStatCard deviation = GrowthCurveViewModel.metricCardsOf(curve).get(1);
        assertNotEquals("无数据", deviation.value);
        assertTrue(deviation.helper.contains("65.0"));
        assertFalse(deviation.helper.contains("按当前标准对比"));
    }
}
