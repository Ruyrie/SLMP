package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L14 扫码 / 批次号解析结果（HTTP 200、code 0，以 result 区分）。 */
@Data
public class BatchLookupVO {

    public static final String OK = "OK";
    public static final String PUBLIC_TRACE = "PUBLIC_TRACE";
    public static final String SWITCH_FARM = "SWITCH_FARM";
    public static final String NO_PERMISSION = "NO_PERMISSION";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID_FORMAT = "INVALID_FORMAT";
    public static final String NOT_BATCH_QR = "NOT_BATCH_QR";

    private String result;
    private String traceToken;
    private String message;
    private String batchNo;
    private String farmName;
    private String role;
    private Long batchId;
    private Long farmId;
}
