package com.zhimu.slmp.account.ui.mine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import org.junit.Test;

/**
 * “我的”页当前养殖场的“养殖场信息”（F05）与“成员管理”（F07）入口：所有者、场长可见，员工和没有养殖场时不显示；
 * 有权限对象时按 can_view_farm_info / can_view_members 判断。
 */
public class MineFarmEntriesTest {

    private static FarmContextVO ctx(String role, String permissions) {
        return TestData.fromJson("{\"farm_id\":1001,\"name\":\"东山猪场\",\"role\":\"" + role + "\""
                + (permissions == null ? "" : ",\"permissions\":" + permissions) + "}", FarmContextVO.class);
    }

    @Test
    public void ownerAndManagerSeeEntries_staffAndNoFarmDoNot() {
        assertTrue(MineViewModel.showFarmInfoOf(ctx("OWNER", null)));
        assertTrue(MineViewModel.showMembersOf(ctx("OWNER", null)));
        assertTrue(MineViewModel.showFarmInfoOf(ctx("MANAGER", null)));
        assertTrue(MineViewModel.showMembersOf(ctx("MANAGER", null)));
        assertFalse(MineViewModel.showFarmInfoOf(ctx("STAFF", null)));
        assertFalse(MineViewModel.showMembersOf(ctx("STAFF", null)));
        assertFalse(MineViewModel.showFarmInfoOf(null));
        assertFalse(MineViewModel.showMembersOf(null));
    }

    @Test
    public void permissionsRespected() {
        FarmContextVO c = ctx("MANAGER", "{\"can_view_farm_info\":true,\"can_view_members\":false}");
        assertTrue(MineViewModel.showFarmInfoOf(c));
        assertFalse(MineViewModel.showMembersOf(c));
    }
}
