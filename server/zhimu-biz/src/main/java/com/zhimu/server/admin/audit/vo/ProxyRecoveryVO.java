package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** R20 客服代登记找回申请的结果。 */
@Data
@Builder
public class ProxyRecoveryVO {

    private Long id;
    private Long userId;
    private String userName;
    private String oldPhone;
    private String newPhone;
    private String status;
    private String statusText;
    private LocalDateTime submittedAt;
}
