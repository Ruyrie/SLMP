package com.zhimu.slmp.feed.widget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.zhimu.slmp.feed.TestRules;
import com.zhimu.slmp.feed.model.vo.FeedExecSummaryVO;
import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/** 生长曲线坐标换算（技术文档 2.7.1）与柱状图纵轴（2.7.2）。 */
public class ChartScaleTest {

    private static final float EPS = 0.01f;

    @Rule
    public TestRules rules = new TestRules();

    @Test
    public void horizontal_dayAgeToPixelAndBack() {
        // 横坐标 = 左 +（日龄 − 起点）÷（终点 − 起点）× 宽
        ChartScale x = ChartScale.horizontal(70, 120, 30f, 500f);
        assertEquals(30f, x.toPixel(70), EPS);
        assertEquals(290f, x.toPixel(96), EPS);
        assertEquals(530f, x.toPixel(120), EPS);
        assertEquals(96f, x.toValue(290f), EPS);
    }

    @Test
    public void vertical_weightToPixel_growsUpward() {
        // 纵坐标 = 底 −（体重 − 最小值）÷（最大值 − 最小值）× 高
        ChartScale y = ChartScale.vertical(20, 120, 400f, 300f);
        assertEquals(400f, y.toPixel(20), EPS);
        assertEquals(100f, y.toPixel(120), EPS);
        assertEquals(280f, y.toPixel(60), EPS);
        assertEquals(60f, y.toValue(280f), EPS);
    }

    @Test
    public void niceRangeAndStep() {
        assertArrayEquals(new int[]{70, 120}, ChartScale.niceRange(78, 111, 10));
        assertArrayEquals(new int[]{60, 110}, ChartScale.niceRange(68, 103, 10));
        assertArrayEquals(new int[]{20, 100}, ChartScale.niceRange(36.9f, 92.4f, 20));
        assertArrayEquals(new int[]{40, 60}, ChartScale.niceRange(40, 40, 20));
        assertEquals(200, ChartScale.niceStep(480, 3));
        assertEquals(1, ChartScale.labelInterval(7, 7));
        assertEquals(5, ChartScale.labelInterval(30, 7));
    }

    @Test
    public void growthModel_fromMock_axisAndLabels() throws IOException {
        GrowthCurveVO vo = TestRules.mock("curve_3001.json", GrowthCurveVO.class);
        GrowthChartModel m = GrowthChartModel.from(vo);
        assertNotNull(m);
        assertEquals(70, m.xStart);
        assertEquals(120, m.xEnd);
        assertEquals(20, m.yMin);
        assertEquals(120, m.yMax);
        assertEquals(3, m.actual.size());
        assertEquals(2, m.latestIndex);
        assertEquals("标准 68.6", m.stdLabel);
        assertEquals("60.0", m.latestLabel);
        assertEquals("10月5日 · 82 日龄 · 48.6 千克（标准 53.2）", TestRules.plain(m.actual.get(1).tip));
        assertEquals(0, m.compare.size());

        GrowthChartModel withCompare = GrowthChartModel.from(TestRules.mock("curve_3001_cmp_3009.json",
                GrowthCurveVO.class));
        assertNotNull(withCompare);
        assertEquals(1, withCompare.compare.size());
        assertEquals(0, withCompare.withoutSeries(3009L).compare.size());
    }

    @Test
    public void barModel_flatPlanLineAndValueLabels() throws IOException {
        BarChartModel m = BarChartModel.from(TestRules.mock("exec_3001_7.json", FeedExecSummaryVO.class));
        assertEquals(7, m.bars.size());
        assertEquals(600, m.yMax);
        assertEquals(200, m.yStep);
        assertEquals(480f, m.planValue, EPS);
        assertEquals("312", m.bars.get(4).valueLabel);
        assertEquals("16日 · 实投 312 千克 · 计划 480 千克 · 完成 2 / 3 餐", TestRules.plain(m.bars.get(4).tip));
    }
}
