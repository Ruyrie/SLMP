package com.zhimu.server.admin.system.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminValidationTest {

    @Test
    void 密码需要8到20位且同时含字母和数字() {
        assertThat(AdminValidation.passwordProblem("Abc12345")).isNull();
        assertThat(AdminValidation.passwordProblem("abc123")).isEqualTo(AdminValidation.MSG_PASSWORD_LENGTH);
        assertThat(AdminValidation.passwordProblem("abcdefgh")).isEqualTo(AdminValidation.MSG_PASSWORD_LETTER_DIGIT);
        assertThat(AdminValidation.passwordProblem("12345678")).isEqualTo(AdminValidation.MSG_PASSWORD_LETTER_DIGIT);
        assertThat(AdminValidation.passwordProblem("Abc 12345")).isEqualTo(AdminValidation.MSG_PASSWORD_LENGTH);
        assertThat(AdminValidation.passwordProblem("A1" + "x".repeat(19)))
                .isEqualTo(AdminValidation.MSG_PASSWORD_LENGTH);
    }

    @Test
    void 原因为空返回40004且文案可定制() {
        assertThatThrownBy(() -> AdminValidation.reason("  ", "请填写新增原因"))
                .isInstanceOf(com.zhimu.server.common.error.BizException.class)
                .satisfies(e -> {
                    var ex = (com.zhimu.server.common.error.BizException) e;
                    assertThat(ex.getCode()).isEqualTo(40004);
                    assertThat(ex.getMessage()).isEqualTo("请填写新增原因");
                });
    }

    @Test
    void 原因超过100字返回40001() {
        assertThatThrownBy(() -> AdminValidation.reason("原".repeat(101), "请填写修改原因"))
                .isInstanceOf(com.zhimu.server.common.error.BizException.class)
                .satisfies(e -> assertThat(((com.zhimu.server.common.error.BizException) e).getCode())
                        .isEqualTo(40001));
    }

    @Test
    void 原因去首尾空白后返回() {
        assertThat(AdminValidation.reason("  内容有误  ", "请填写修改原因")).isEqualTo("内容有误");
    }
}
