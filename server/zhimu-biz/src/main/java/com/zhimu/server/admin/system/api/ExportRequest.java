package com.zhimu.server.admin.system.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次导出的内容描述：调用方按页面查询好表头与行数据，ExportService 负责生成 xlsx、
 * 写 t_file（scene = EXPORT、7 天有效、bound = 1）、签发 10 分钟下载地址并写 EXPORT 日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    /** 文件名（不含扩展名），例如 用户账号_20261019 */
    private String fileName;
    private String sheetName;
    @Builder.Default
    private List<String> headers = new ArrayList<>();
    @Builder.Default
    private List<List<Object>> rows = new ArrayList<>();
    /** 被导出的业务类型（BIZ_TYPE），用于 EXPORT 日志的 biz_type */
    private String bizType;
    private Long bizId;
    /** 筛选条件，写入 EXPORT 日志 extra_text */
    @Builder.Default
    private Map<String, Object> filters = new LinkedHashMap<>();
    /** 行数（含表头之外的数据行），由调用方统计，最多 50,000 */
    private Integer rowCount;
}
