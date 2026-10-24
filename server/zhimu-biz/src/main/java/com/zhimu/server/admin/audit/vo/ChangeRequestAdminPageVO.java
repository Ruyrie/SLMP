package com.zhimu.server.admin.audit.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** ADM-R10 分页结果：带 tab_counts 与影响可出栏日期的待审核数。 */
@Getter
@Setter
public class ChangeRequestAdminPageVO extends PageVO<ChangeRequestAdminItemVO> {

    private Integer sellableDateCount;

    public ChangeRequestAdminPageVO() {
    }

    public ChangeRequestAdminPageVO(List<ChangeRequestAdminItemVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}
