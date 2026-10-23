package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 导出结果（ADM-S14/S19/S32、R22）：url 为 10 分钟有效的下载地址。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportVO {
    private Long fileId;
    private String fileName;
    private Integer rowCount;
    private String url;
    private LocalDateTime expireAt;
}
