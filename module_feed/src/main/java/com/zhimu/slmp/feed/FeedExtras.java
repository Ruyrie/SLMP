package com.zhimu.slmp.feed;

import androidx.annotation.Nullable;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件的页面参数键与业务类型。RouterPath 中已有的键（EXTRA_TASK_ID、EXTRA_BATCH_ID、EXTRA_PEN_ID 等）直接用 RouterPath；
 * 下面三个键 RouterPath 中还没有，先在本组件定义，交付时由主会话合并进 RouterPath：
 * RouterPath.EXTRA_RECORD_ID（投喂记录，G02 修改 / 只读）、RouterPath.EXTRA_WEIGH_RECORD_ID（称重记录，G05 修改）、RouterPath.EXTRA_FEED_ID（M03 预填饲料）。
 */
public final class FeedExtras {


    public static final String BIZ_FEED_RECORD = "FEED_RECORD";
    public static final String BIZ_FEED_PLAN = "FEED_PLAN";
    public static final String BIZ_FEED_TASK = "FEED_TASK";
    public static final String BIZ_WEIGH_RECORD = "WEIGH_RECORD";

    private FeedExtras() {
    }

    /** 页面参数中的 ID：Bundle 中可能是 long、int 或字符串（推送与待办链接按字符串传） */
    @Nullable
    public static Long readId(@Nullable Object value) {
        if (value instanceof Number) {
            long v = ((Number) value).longValue();
            return v > 0 ? v : null;
        }
        if (value instanceof String) {
            try {
                long v = Long.parseLong(((String) value).trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean readBoolean(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value instanceof String && Boolean.parseBoolean((String) value);
    }
}
