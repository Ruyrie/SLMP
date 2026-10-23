package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * SSE error：超时、中断、失败。status：TIMEOUT / FAILED / INTERRUPTED。
 */
@Data
public class StreamErrorVO {

    private int code;
    private String message;
    private String status;
}
