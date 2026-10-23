package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S02 登录响应：管理员、强制改密、权限码与菜单；会话令牌只在 Cookie 中，不出现在响应体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginVO {
    private AdminProfileVO admin;
    private Boolean mustChangePassword;
    private LocalDateTime tempPasswordExpireAt;
    private Integer idleMinutes;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<MenuGroupVO> menus = new ArrayList<>();
}
