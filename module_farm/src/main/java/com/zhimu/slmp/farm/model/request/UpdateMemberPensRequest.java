package com.zhimu.slmp.farm.model.request;

import java.util.ArrayList;
import java.util.List;

/**
 * 调整负责栏舍（API-F24）：pen_ids 可为 0 个，version 取 member.version。
 */
public class UpdateMemberPensRequest {

    private final List<Long> penIds;
    private final int version;

    public UpdateMemberPensRequest(List<Long> penIds, int version) {
        this.penIds = new ArrayList<>(penIds);
        this.version = version;
    }

    public List<Long> getPenIds() {
        return penIds;
    }

    public int getVersion() {
        return version;
    }
}
