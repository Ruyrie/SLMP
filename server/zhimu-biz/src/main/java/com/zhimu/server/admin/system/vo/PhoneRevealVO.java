package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

/** R24 查看完整手机号：仅返回本次查看的完整号码，响应头 no-store，不写日志明文。 */
@Data
@Builder
public class PhoneRevealVO {

    private Long userId;
    private String name;
    /** 完整手机号，例如 13800006620 */
    private String phone;
}
