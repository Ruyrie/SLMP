package com.zhimu.server.admin.audit.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** ADM-R02 分页结果：除 PageVO 外带超时统计与最多 5 个超时养殖场名称。 */
@Getter
@Setter
public class FarmAuditPageVO extends PageVO<FarmAuditItemVO> {

    private Integer overdueCount;
    private List<String> overdueFarmNames = new ArrayList<>();

    public FarmAuditPageVO() {
    }

    public FarmAuditPageVO(List<FarmAuditItemVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}
