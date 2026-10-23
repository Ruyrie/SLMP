package com.zhimu.server.biz.farm.request;

import lombok.Data;

import java.util.List;

/** 调整负责栏舍（API-F24）：pen_ids 可为 0 个 */
@Data
public class UpdateMemberPensRequest {
    private List<Long> penIds;
    private Integer version;
}
