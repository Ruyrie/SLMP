package com.zhimu.slmp.base.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;

/** 数量、金额、百分比显示（02 号规范 9.3 节）；数值与中文单位之间为不换行空格 */
public class NumberFormatterTest {

    private static final String SP = NumberFormatter.NBSP;

    @Test
    public void quantityWithThousands() {
        assertEquals("1,200" + SP + "千克", NumberFormatter.formatQuantity(1200, "千克"));
        assertEquals("200" + SP + "头", NumberFormatter.formatQuantity(200, "头"));
        assertEquals("18,600" + SP + "只", NumberFormatter.formatQuantity(18600L, "只"));
    }

    @Test
    public void decimalQuantity() {
        assertEquals("2.5" + SP + "千克", NumberFormatter.formatQuantity(new BigDecimal("2.50"), "千克"));
        assertEquals("60.0" + SP + "千克", NumberFormatter.formatQuantity(new BigDecimal("60"), 1, "千克"));
    }

    @Test
    public void latinUnitFollowsNumber() {
        assertEquals("5MB", NumberFormatter.formatQuantity(5, "MB"));
    }

    @Test
    public void amount() {
        assertEquals("3,200.00" + SP + "元", NumberFormatter.formatAmount(new BigDecimal("3200")));
        assertEquals("188,160" + SP + "元", NumberFormatter.formatAmountSummary(new BigDecimal("188160.00")));
        assertEquals("68,448.50" + SP + "元", NumberFormatter.formatAmountSummary(new BigDecimal("68448.5")));
    }

    @Test
    public void percent() {
        assertEquals("92.0%", NumberFormatter.formatPercent(new BigDecimal("0.92")));
        assertEquals("+32.1%", NumberFormatter.formatSignedPercent(new BigDecimal("0.321")));
        assertEquals("−16.0%", NumberFormatter.formatSignedPercent(new BigDecimal("-0.16")));
    }

    @Test
    public void nullAndPhone() {
        assertEquals("", NumberFormatter.formatQuantity(null, "头"));
        assertEquals("138****6620", NumberFormatter.maskPhone("13800006620"));
    }
}
