package com.zhimu.slmp.inspect.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 现场判定：≤ 1000 米现场、&gt; 1000 米非现场、没有位置为“无位置”，以及球面距离。
 */
public class SiteJudgeTest {

    private static final double FARM_LAT = 42.3025;
    private static final double FARM_LNG = 118.8516;

    @Test
    public void judge_boundaryAt1000Meters() {
        assertEquals(SiteJudge.State.ON_SITE, SiteJudge.judge(0));
        assertEquals(SiteJudge.State.ON_SITE, SiteJudge.judge(120));
        assertEquals(SiteJudge.State.ON_SITE, SiteJudge.judge(1000));
        assertEquals(SiteJudge.State.OFF_SITE, SiteJudge.judge(1001));
        assertEquals(SiteJudge.State.OFF_SITE, SiteJudge.judge(3200));
    }

    @Test
    public void judge_noLocation() {
        assertEquals(SiteJudge.State.NO_LOCATION, SiteJudge.judge(null));
        assertEquals(SiteJudge.State.NO_LOCATION, SiteJudge.judge(-1));
    }

    @Test
    public void distance_samePointIsZero() {
        assertEquals(0, SiteJudge.distanceMeters(FARM_LAT, FARM_LNG, FARM_LAT, FARM_LNG));
    }

    @Test
    public void distance_northOffsets() {
        // 纬度 1° ≈ 111.195 千米
        int d1 = SiteJudge.distanceMeters(FARM_LAT + 0.001, FARM_LNG, FARM_LAT, FARM_LNG);
        assertTrue("about 111 m: " + d1, d1 >= 110 && d1 <= 112);
        int d2 = SiteJudge.distanceMeters(FARM_LAT + 0.0108, FARM_LNG, FARM_LAT, FARM_LNG);
        assertTrue("about 1201 m: " + d2, d2 >= 1195 && d2 <= 1205);
        assertEquals(SiteJudge.State.OFF_SITE, SiteJudge.judge(d2));
    }

    @Test
    public void distance_eastOffsetShrinksWithLatitude() {
        // 北纬 42.3°：经度 0.01° ≈ 822 米
        int d = SiteJudge.distanceMeters(FARM_LAT, FARM_LNG + 0.01, FARM_LAT, FARM_LNG);
        assertTrue("about 822 m: " + d, d >= 815 && d <= 830);
        assertEquals(SiteJudge.State.ON_SITE, SiteJudge.judge(d));
    }

    @Test
    public void distance_nullWhenAnyCoordinateMissing() {
        assertNull(SiteJudge.distance(null, FARM_LNG, FARM_LAT, FARM_LNG));
        assertNull(SiteJudge.distance(FARM_LAT, FARM_LNG, null, null));
        assertEquals(Integer.valueOf(0), SiteJudge.distance(FARM_LAT, FARM_LNG, FARM_LAT, FARM_LNG));
        assertEquals(SiteJudge.State.NO_LOCATION, SiteJudge.judge(SiteJudge.distance(FARM_LAT, FARM_LNG, null, FARM_LNG)));
    }

    @Test
    public void text_byState() {
        assertEquals("现场 · 距养殖场 120 米", SiteJudge.text(SiteJudge.State.ON_SITE, 120));
        assertEquals("距养殖场 3.2 千米", SiteJudge.text(SiteJudge.State.OFF_SITE, 3200));
        assertEquals("距养殖场 1 千米", SiteJudge.text(SiteJudge.State.OFF_SITE, 1001));
        assertEquals("未获得位置，不影响登记", SiteJudge.text(SiteJudge.State.NO_LOCATION, null));
    }
}
