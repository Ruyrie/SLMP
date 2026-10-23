package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S21 登录账号查重：available = true 表示可以使用（查重包含已停用账号）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsernameCheckVO {
    private Boolean available;
}
