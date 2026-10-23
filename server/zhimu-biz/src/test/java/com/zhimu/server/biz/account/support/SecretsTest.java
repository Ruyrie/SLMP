package com.zhimu.server.biz.account.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretsTest {

    @Test
    void 恢复码为16位且只含去混字符() {
        for (int i = 0; i < 200; i++) {
            String code = Secrets.recoveryCode();
            assertThat(code).hasSize(16);
            assertThat(code).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}");
            assertThat(code).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void 临时密码为10位字母数字且不含易混字符() {
        for (int i = 0; i < 100; i++) {
            String password = Secrets.tempPassword();
            assertThat(password).hasSize(10);
            assertThat(password).matches("[23456789a-zA-Z]{10}");
            assertThat(password).doesNotContain("0", "o", "1", "l", "I", "O");
        }
    }

    @Test
    void 手机号摘要为64位十六进制() {
        assertThat(Secrets.sha256Hex("13800006620")).matches("[0-9a-f]{64}");
        assertThat(Secrets.sha256Hex("13800006620")).isEqualTo(Secrets.sha256Hex("13800006620"));
    }
}
