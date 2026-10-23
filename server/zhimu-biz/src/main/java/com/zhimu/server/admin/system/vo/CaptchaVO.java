package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S01 图形验证码：PNG 112×40 的 data URL，有效 120 秒，一次有效。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO {
    private String captchaId;
    private String image;
    private Integer expireSeconds;
}
