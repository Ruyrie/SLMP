package com.zhimu.slmp.today.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationChannelsTest {

    private Context context;
    private NotificationManager manager;

    @Before
    public void setUp() {
        context = mock(Context.class);
        manager = mock(NotificationManager.class);
        when(context.getSystemService(NotificationManager.class)).thenReturn(manager);
    }

    @Test
    public void oldDefaultChannels_migrateToHeadsUpImportance() {
        previous("zm_todo", NotificationManager.IMPORTANCE_DEFAULT, false);
        previous("zm_system", NotificationManager.IMPORTANCE_DEFAULT, false);
        Map<String, Integer> importance = create();
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_HIGH), importance.get(NotificationChannels.TODO));
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_HIGH), importance.get(NotificationChannels.SYSTEM));
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_HIGH), importance.get(NotificationChannels.URGENT));
    }

    @Test
    public void userLoweredOrBlockedChannels_keepUserChoice() {
        previous("zm_todo", NotificationManager.IMPORTANCE_LOW, true);
        previous("zm_system", NotificationManager.IMPORTANCE_NONE, false);
        Map<String, Integer> importance = create();
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_LOW), importance.get(NotificationChannels.TODO));
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_NONE), importance.get(NotificationChannels.SYSTEM));
    }

    @Test
    public void existingNewChannel_isReusedWithoutResettingUserSettings() {
        NotificationChannel existing = mock(NotificationChannel.class);
        when(manager.getNotificationChannel(NotificationChannels.TODO)).thenReturn(existing);
        Map<String, Integer> importance = create();
        assertEquals(2, importance.size());
        verify(existing).setName(null);
    }

    private NotificationChannel previous(String id, int importance, boolean userSet) {
        NotificationChannel previous = mock(NotificationChannel.class);
        when(previous.getImportance()).thenReturn(importance);
        when(previous.hasUserSetImportance()).thenReturn(userSet);
        when(manager.getNotificationChannel(id)).thenReturn(previous);
        return previous;
    }

    private Map<String, Integer> create() {
        Map<String, Integer> importance = new HashMap<>();
        try (MockedConstruction<NotificationChannel> ignored = mockConstruction(NotificationChannel.class,
                (channel, invocation) -> importance.put((String) invocation.arguments().get(0),
                        (Integer) invocation.arguments().get(2)))) {
            NotificationChannels.createAll(context);
        }
        return importance;
    }
}
