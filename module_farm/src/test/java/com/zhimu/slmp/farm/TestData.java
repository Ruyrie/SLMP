package com.zhimu.slmp.farm;

import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.PageVO;

import java.util.Arrays;
import java.util.Collections;

/**
 * 测试数据（与 App 开发约定 4.4 节一致）：VO 没有 setter，用 Gson 按 JSON 构造。
 */
public final class TestData {

    private TestData() {
    }

    public static FarmContextVO context(long farmId, String name, String role, String status) {
        String json = "{\"farm_id\":" + farmId + ",\"name\":\"" + name + "\",\"role\":\"" + role
                + "\",\"status\":\"" + status + "\",\"permissions\":{\"can_view_farm_info\":true}}";
        return GsonFactory.get().fromJson(json, FarmContextVO.class);
    }

    public static FarmListItemVO item(long farmId, String name, String status, boolean canSwitch, boolean current) {
        String json = "{\"farm_id\":" + farmId + ",\"name\":\"" + name + "\",\"status\":\"" + status
                + "\",\"role\":\"OWNER\",\"role_text\":\"所有者\",\"can_switch\":" + canSwitch
                + ",\"is_current\":" + current + "}";
        return GsonFactory.get().fromJson(json, FarmListItemVO.class);
    }

    public static PageVO<FarmListItemVO> page(FarmListItemVO... items) {
        return new PageVO<>(Arrays.asList(items), items.length, 1, 20, false, Collections.emptyMap());
    }
}
