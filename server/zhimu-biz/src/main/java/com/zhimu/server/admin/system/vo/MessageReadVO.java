package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S42 标记已读结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadVO {
    private Integer updatedCount;
}
