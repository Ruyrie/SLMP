package com.zhimu.slmp.farm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 表单规则（与服务端模块02 文档一致）与手机号 3-4-4 格式化。
 */
public class FarmRulesTest {

    @Test
    public void phone_elevenDigitsStartingWithOne() {
        assertTrue(FarmRules.isPhone("13800006620"));
        assertTrue(FarmRules.isPhone("138 0000 6620"));
        assertFalse(FarmRules.isPhone("1380000662"));
        assertFalse(FarmRules.isPhone("238 0000 6620"));
        assertFalse(FarmRules.isPhone("138000066201"));
    }

    @Test
    public void scale_integerBetweenOneAndMax() {
        assertEquals(Integer.valueOf(120), FarmRules.parseScale("120"));
        assertEquals(Integer.valueOf(999999), FarmRules.parseScale("999999"));
        assertNull(FarmRules.parseScale("0"));
        assertNull(FarmRules.parseScale("1000000"));
        assertNull(FarmRules.parseScale("12.5"));
        assertNull(FarmRules.parseScale(""));
    }

    @Test
    public void phoneFormat_groups344() {
        assertEquals("138 0000 6620", PhoneTextWatcher.format("13800006620"));
        assertEquals("139 04", PhoneTextWatcher.format("13904"));
        assertEquals("13800006620", PhoneTextWatcher.digits("138 0000 6620"));
    }
}
