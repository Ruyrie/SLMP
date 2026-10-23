package com.zhimu.server.admin.system.support;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPasswordsTest {

    @Test
    void 临时密码为10位字母数字且同时包含字母和数字() {
        for (int i = 0; i < 100; i++) {
            String password = AdminPasswords.tempPassword();
            assertThat(password).hasSize(10).matches("[A-Za-z0-9]{10}")
                    .matches(".*[A-Za-z].*")
                    .matches(".*\\d.*");
        }
    }

    @RepeatedTest(5)
    void 每次生成不同密码() {
        assertThat(AdminPasswords.tempPassword()).isNotEqualTo(AdminPasswords.tempPassword());
    }
}
