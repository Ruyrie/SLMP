package com.zhimu.server.integration.sms;

import com.zhimu.server.common.util.Masks;
import lombok.extern.slf4j.Slf4j;

/**
 * mock 模式：不发送短信，验证码固定为配置的 mock-code（开发环境默认 123456）。
 */
@Slf4j
public class MockSmsClient implements SmsClient {

    private final SmsProperties props;

    public MockSmsClient(SmsProperties props) {
        this.props = props;
    }

    @Override
    public SmsMode mode() {
        return SmsMode.MOCK;
    }

    @Override
    public SmsSendResult send(String phone, SmsTemplate template) {
        log.info("短信 mock 发送 phone={} template={}，未真实发送，验证码为 mock-code（{}）", Masks.phone(phone), template,
                props.getMockCode());
        return new SmsSendResult(null, null, props.getIntervalSeconds(), props.getValidSeconds());
    }

    @Override
    public boolean verify(String phone, String code) {
        boolean pass = code != null && code.equals(props.getMockCode());
        log.info("短信 mock 校验 phone={} result={}", Masks.phone(phone), pass ? "PASS" : "FAIL");
        return pass;
    }
}
