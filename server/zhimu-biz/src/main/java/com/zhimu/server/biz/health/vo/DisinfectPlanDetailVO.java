package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D29 消毒计划详情：列表项 + 目录、档案与栏舍执行人。 */
@Data
public class DisinfectPlanDetailVO extends DisinfectPlanVO {

    private Long catalogId;
    private Long drugId;
    private List<Pen> pens = new ArrayList<>();

    @Data
    public static class Pen {
        private Long penId;
        private String penName;
        /** 李明 / 你和刘芳 / 无负责员工 */
        private String executorText;
    }
}
