package com.zhimu.server.integration.sms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockSmsClientTest {

    @Test
    void mock_sendDoesNothing_verifyMatchesMockCode() {
        SmsProperties props = new SmsProperties();
        props.setMockCode("123456");
        MockSmsClient client = new MockSmsClient(props);

        assertThat(client.mode()).isEqualTo(SmsMode.MOCK);
        assertThat(client.send("13800006620", SmsTemplate.LOGIN_REGISTER))
                .isEqualTo(new SmsSendResult(null, null, 60, 300));
        assertThat(client.verify("13800006620", "123456")).isTrue();
        assertThat(client.verify("13800006620", "654321")).isFalse();
        assertThat(client.verify("13800006620", null)).isFalse();
    }

    @Test
    void config_selectsClientByMode() {
        SmsProperties props = new SmsProperties();
        assertThat(new SmsConfig().smsClient(props)).isInstanceOf(MockSmsClient.class);
        props.setMode(SmsMode.ALIYUN);
        // 未配置 Key 也能创建（发送时返回 50301）
        assertThat(new SmsConfig().smsClient(props)).isInstanceOf(AliyunSmsClient.class);
    }
}
