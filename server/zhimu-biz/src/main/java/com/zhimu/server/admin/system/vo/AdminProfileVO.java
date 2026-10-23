package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前管理员（AdminLoginVO.admin、ADM-S05）。角色编码另带中文名。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileVO {
    private Long adminId;
    private String name;
    private String username;
    private String roleCode;
    private String roleCodeText;
}
