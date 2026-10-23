package com.zhimu.server.biz.ai.query;

import lombok.Getter;
import lombok.Setter;

/**
 * API-K03 按轮次切片：before_round_no 与 after_round_no 互斥，size 单位为轮（默认 20，最大 50）。
 */
@Getter
@Setter
public class AiMessageQuery {

    private Integer beforeRoundNo;
    private Integer afterRoundNo;
    private Integer size;
}
