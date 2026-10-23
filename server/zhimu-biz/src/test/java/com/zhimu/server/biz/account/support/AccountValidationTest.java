package com.zhimu.server.biz.account.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountValidationTest {

    @Test
    void 手机号去空格并校验11位() {
        assertThat(AccountValidation.phone(" 138 0000 6620 ")).isEqualTo("13800006620");
        assertThat(AccountValidation.isValidPhone("13800006620")).isTrue();
        assertThat(AccountValidation.isValidPhone("23800006620")).isFalse();
        assertThat(AccountValidation.isValidPhone("1380000662")).isFalse();
    }

    @Test
    void 密码规则_长度字母数字与字符集() {
        assertThat(AccountValidation.passwordProblem("Zmt12345")).isNull();
        assertThat(AccountValidation.passwordProblem("Abc123!@")).isNull();
        assertThat(AccountValidation.passwordProblem("Abc1234")).isEqualTo(AccountValidation.MSG_PASSWORD_LENGTH);
        assertThat(AccountValidation.passwordProblem("Abcdefgh")).isEqualTo(AccountValidation.MSG_PASSWORD_LETTER_DIGIT);
        assertThat(AccountValidation.passwordProblem("12345678")).isEqualTo(AccountValidation.MSG_PASSWORD_LETTER_DIGIT);
        assertThat(AccountValidation.passwordProblem("Abc 12345")).isEqualTo(AccountValidation.MSG_PASSWORD_LENGTH);
        assertThat(AccountValidation.passwordProblem("密码密码密码A1")).isEqualTo(AccountValidation.MSG_PASSWORD_LENGTH);
    }

    @Test
    void 恢复码规范化_去空格短横线并转大写() {
        assertThat(AccountValidation.recoveryCode("8kq4-t2mw 7hxn-3pdr")).isEqualTo("8KQ4T2MW7HXN3PDR");
        assertThat(AccountValidation.isValidRecoveryCode("8KQ4T2MW7HXN3PDR")).isTrue();
        assertThat(AccountValidation.isValidRecoveryCode("8KQ4T2MW7HXN3PD")).isFalse();
    }

    @Test
    void 姓名与情况说明按Unicode码点计数() {
        String emoji = "😀";
        assertThat(AccountValidation.length(emoji)).isEqualTo(1);
        assertThat(AccountValidation.nameProblem(emoji.repeat(AccountValidation.NAME_MAX))).isNull();
        assertThat(AccountValidation.nameProblem(emoji.repeat(AccountValidation.NAME_MAX + 1)))
                .isEqualTo(AccountValidation.MSG_NAME_TOO_LONG);
        assertThat(AccountValidation.nameProblem("   ")).isEqualTo(AccountValidation.MSG_NAME_EMPTY);
        assertThat(AccountValidation.trim("  王建国 ")).isEqualTo("王建国");
        assertThat(AccountValidation.descriptionProblem("x".repeat(201)))
                .isEqualTo(AccountValidation.MSG_DESCRIPTION);
        assertThat(AccountValidation.descriptionProblem("x".repeat(200))).isNull();
    }
}
