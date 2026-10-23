package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** API-D36 消毒任务分页：追加逾期横幅，员工不输出。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DisinfectTaskPageVO extends PageVO<DisinfectTaskVO> {

    private OverdueBanner overdueBanner;

    @Data
    public static class OverdueBanner {
        private Boolean show;
        private String text;
    }
}
