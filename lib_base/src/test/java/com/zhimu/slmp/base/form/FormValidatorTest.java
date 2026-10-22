package com.zhimu.slmp.base.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

public class FormValidatorTest {

    @Test
    public void validLogin() {
        FormValidator v = new FormValidator().phone("phone", "138 0000 6620").password("password", "Zmt12345");
        assertTrue(v.isValid());
        assertTrue(v.validate().isEmpty());
    }

    @Test
    public void phoneRules() {
        assertEquals(FormValidator.MSG_PHONE_EMPTY, new FormValidator().phone("phone", " ").validate().get("phone"));
        assertEquals(FormValidator.MSG_PHONE_INVALID, new FormValidator().phone("phone", "1380000662").validate().get("phone"));
        assertEquals(FormValidator.MSG_PHONE_INVALID, new FormValidator().phone("phone", "23800006620").validate().get("phone"));
    }

    @Test
    public void passwordRules() {
        assertFalse(FormValidator.isPassword("Zmt1234"));
        assertFalse(FormValidator.isPassword("zmtzmtzmt"));
        assertFalse(FormValidator.isPassword("123456789"));
        assertFalse(FormValidator.isPassword("Zmt123456789012345678"));
        assertTrue(FormValidator.isPassword("abcdefg1"));
    }

    @Test
    public void smsCode() {
        assertTrue(new FormValidator().smsCode("sms_code", "123456").isValid());
        assertEquals(FormValidator.MSG_SMS_CODE_INVALID,
                new FormValidator().smsCode("sms_code", "12345a").validate().get("sms_code"));
    }

    @Test
    public void firstErrorPerFieldInOrder() {
        Map<String, String> errors = new FormValidator()
                .required("name", "", "请输入姓名")
                .maxLength("name", "", 10, "姓名不能超过 10 个字")
                .range("weight", "1200", BigDecimal.ONE, new BigDecimal("1000"), "重量需在 1–1000 千克之间")
                .maxLength("remark", "超过五个字的备注", 5, "备注不能超过 5 个字")
                .validate();
        assertEquals("请输入姓名", errors.get("name"));
        assertEquals("重量需在 1–1000 千克之间", errors.get("weight"));
        assertEquals("备注不能超过 5 个字", errors.get("remark"));
        assertEquals("name", errors.keySet().iterator().next());
    }

    @Test
    public void rangeAndScale() {
        assertTrue(new FormValidator().range("count", "200", 1, 205, "x").isValid());
        assertFalse(new FormValidator().range("count", "abc", 1, 205, "x").isValid());
        assertFalse(new FormValidator().maxScale("amount", "12.345", 2, "x").isValid());
        assertTrue(new FormValidator().maxScale("amount", "12.30", 2, "x").isValid());
    }
}
