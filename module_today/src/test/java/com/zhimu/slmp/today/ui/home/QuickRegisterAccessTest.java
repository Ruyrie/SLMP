package com.zhimu.slmp.today.ui.home;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.FarmPermissionVO;
import com.zhimu.slmp.today.R;

import org.junit.Test;

public class QuickRegisterAccessTest {
    private FarmContextVO context(String role, String status, boolean writable) {
        FarmContextVO context = mock(FarmContextVO.class);
        FarmPermissionVO permissions = mock(FarmPermissionVO.class);
        when(context.getFarmId()).thenReturn(1L);
        when(context.getRole()).thenReturn(role);
        when(context.getStatus()).thenReturn(status);
        when(context.getPermissions()).thenReturn(permissions);
        when(permissions.isWritable()).thenReturn(writable);
        return context;
    }

    @Test
    public void requiresASelectedFarm() {
        assertEquals(R.string.today_quick_no_farm, QuickRegisterAccess.blockedReason(null));
        FarmContextVO farm = context("OWNER", "NORMAL", true);
        when(farm.getFarmId()).thenReturn(0L);
        assertEquals(R.string.today_quick_no_farm, QuickRegisterAccess.blockedReason(farm));
    }

    @Test
    public void respectsServerWritePermissionForEveryRole() {
        for (String role : new String[]{"OWNER", "MANAGER", "STAFF"}) {
            assertEquals(0, QuickRegisterAccess.blockedReason(context(role, "NORMAL", true)));
            assertEquals(R.string.today_quick_not_writable,
                    QuickRegisterAccess.blockedReason(context(role, "NORMAL", false)));
        }
    }

    @Test
    public void doesNotTreatUnapprovedOrSuspendedFarmsAsWritable() {
        for (String status : new String[]{"PENDING", "REJECTED", "DISABLED", "FROZEN"}) {
            assertEquals(R.string.today_quick_not_writable,
                    QuickRegisterAccess.blockedReason(context("OWNER", status, true)));
        }
    }

    @Test
    public void requiresKnownRoleAndCurrentPermissions() {
        FarmContextVO farm = context("OWNER", "NORMAL", true);
        when(farm.getPermissions()).thenReturn(null);
        assertEquals(R.string.today_quick_not_writable, QuickRegisterAccess.blockedReason(farm));
        assertEquals(R.string.today_quick_not_writable,
                QuickRegisterAccess.blockedReason(context("UNKNOWN", "NORMAL", true)));
    }
}
