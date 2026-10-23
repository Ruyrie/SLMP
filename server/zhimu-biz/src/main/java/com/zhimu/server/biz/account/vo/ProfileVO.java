package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A16 修改姓名的结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileVO {
    private String name;
    private String nameInitial;
    private Integer version;
}
