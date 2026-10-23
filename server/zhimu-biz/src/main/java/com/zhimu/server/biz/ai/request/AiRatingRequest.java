package com.zhimu.server.biz.ai.request;

import lombok.Getter;
import lombok.Setter;

/**
 * API-K09 评价：rating 为 USEFUL / USELESS / null（取消评价）。
 */
@Getter
@Setter
public class AiRatingRequest {

    private String rating;
}
