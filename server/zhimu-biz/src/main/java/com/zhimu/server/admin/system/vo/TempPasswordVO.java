package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仅 dev 联调返回的临时密码（ADM-S22、S24）。生产环境控制器不输出该字段，
 * 密码只通过短信下发（本批不发送）；不写日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempPasswordVO {
    private String tempPassword;
}
