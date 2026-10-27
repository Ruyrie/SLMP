package com.zhimu.slmp.farm.ui.member;

import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.network.GsonFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvitePagingAdapterTest {
    @Test public void acceptedInviteDoesNotShowAnExpiryDeadline() {
        InviteItemVO item = GsonFactory.get().fromJson("{\"status\":\"ACCEPTED\",\"handled_at\":\"2026-10-02 10:00:00\",\"expires_at\":\"2026-10-08 09:00:00\"}", InviteItemVO.class);
        assertTrue(InvitePagingAdapter.line3(item).contains("已加入养殖场"));
        assertFalse(InvitePagingAdapter.line3(item).contains("前有效"));
    }

    @Test public void acceptedLegacyInviteWithoutHandledTimeStillShowsSuccess() {
        InviteItemVO item = GsonFactory.get().fromJson("{\"status\":\"ACCEPTED\"}", InviteItemVO.class);
        assertEquals("已加入养殖场", InvitePagingAdapter.line3(item));
    }
}
