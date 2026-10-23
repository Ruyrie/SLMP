package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ADM-S22 新增管理员结果：admin_id；dev 环境额外返回 temp_password 供联调，生产环境不返回也不写日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatedVO {
    private Long adminId;
    private String tempPassword;
}
