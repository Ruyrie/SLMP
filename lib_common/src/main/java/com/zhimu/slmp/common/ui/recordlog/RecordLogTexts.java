package com.zhimu.slmp.common.ui.recordlog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.RecordLogVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 修改记录（API-U14）每一条的显示文字：主文字“10月12日 14:20 · 刘芳”；辅助文字依次为操作（普通修改且有字段变化时不写）、
 * 每个字段一行“容量：200 头 → 220 头”、“原因：……”、“修改申请 CR2610190008”。不依赖 Android 类，便于单元测试。
 */
public final class RecordLogTexts {

    public static final String DOT = " · ";
    public static final String ARROW = " → ";
    /** 原值或新值为空 */
    public static final String NONE = "无";
    /** 普通修改（op_action）：有字段变化时不再单独写操作名称 */
    public static final String ACTION_UPDATE = "UPDATE";

    private RecordLogTexts() {
    }

    /** 主文字：修改时间 · 修改人 */
    @NonNull
    public static String title(@NonNull RecordLogVO log) {
        String time = DateFormatter.formatDateTime(log.getCreatedAt());
        String who = log.getOperator() == null ? null : log.getOperator().getName();
        if (isEmpty(who)) {
            return time;
        }
        return isEmpty(time) ? who : time + DOT + who;
    }

    /** 辅助文字（多行） */
    @NonNull
    public static String content(@NonNull RecordLogVO log) {
        List<String> lines = new ArrayList<>();
        List<RecordLogVO.Change> changes = log.getChanges();
        String action = log.getOpActionText();
        if (!isEmpty(action) && (changes.isEmpty() || !ACTION_UPDATE.equals(log.getOpAction()))) {
            lines.add(action);
        }
        for (RecordLogVO.Change c : changes) {
            lines.add(changeText(c));
        }
        if (!isEmpty(log.getReason())) {
            lines.add("原因：" + log.getReason());
        }
        if (!isEmpty(log.getChangeRequestNo())) {
            lines.add("修改申请 " + log.getChangeRequestNo());
        }
        return String.join("\n", lines);
    }

    /** 一项字段变化：“字段：原值 → 新值”，值为空写“无” */
    @NonNull
    public static String changeText(@NonNull RecordLogVO.Change c) {
        String field = c.getFieldName();
        String values = orNone(c.getBefore()) + ARROW + orNone(c.getAfter());
        return isEmpty(field) ? values : field + "：" + values;
    }

    @NonNull
    private static String orNone(@Nullable String v) {
        return isEmpty(v) ? NONE : v;
    }

    private static boolean isEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
