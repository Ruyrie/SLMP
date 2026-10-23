package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 记录中的登记人、审核人、成员 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {
    private Long userId;
    private String name;
    private String avatarUrl;
    private String role;
    private String roleText;
}