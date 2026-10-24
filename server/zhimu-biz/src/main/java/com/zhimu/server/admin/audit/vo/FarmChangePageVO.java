package com.zhimu.server.admin.audit.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADM-R06 分页结果：PageVO + tab_counts + 指标卡 stats。 */
@Getter
@Setter
public class FarmChangePageVO extends PageVO<FarmChangeItemVO> {

    private Stats stats = new Stats();

    public FarmChangePageVO() {
    }

    public FarmChangePageVO(List<FarmChangeItemVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }

    @Getter
    @Setter
    public static class Stats {
        private Integer pendingCount;
        private LocalDateTime earliestAt;
        private Integer approved30d;
        /** NAME / ADDRESS / QUALIFICATION，一条可计多项 */
        private Map<String, Integer> approved30dByItem = new LinkedHashMap<>();
        private Integer rejected30d;
    }
}
