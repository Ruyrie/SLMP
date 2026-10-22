package com.zhimu.slmp.health.ui.disinfecttask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.health.model.vo.DisinfectTaskVO;

import java.util.Objects;

/**
 * D09 列表的界面模型：分组标题（“已逾期 · 1”，可在上方带逾期提醒横幅）、只有横幅的行，或一条任务。
 */
public final class DisinfectTaskRow {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_TASK = 1;
    public static final int TYPE_BANNER = 2;

    final int type;
    /** 分组状态（OVERDUE / PENDING / DONE） */
    @Nullable
    final String status;
    /** 标题上方的逾期提醒横幅（逾期分组之后的第一个标题，或逾期分组在最后时单独一行） */
    @Nullable
    final String banner;
    @Nullable
    final DisinfectTaskVO task;

    private DisinfectTaskRow(int type, @Nullable String status, @Nullable String banner, @Nullable DisinfectTaskVO task) {
        this.type = type;
        this.status = status;
        this.banner = banner;
        this.task = task;
    }

    @NonNull
    public static DisinfectTaskRow header(@NonNull String status, @Nullable String banner) {
        return new DisinfectTaskRow(TYPE_HEADER, status, banner, null);
    }

    @NonNull
    public static DisinfectTaskRow banner(@NonNull String banner) {
        return new DisinfectTaskRow(TYPE_BANNER, null, banner, null);
    }

    @NonNull
    public static DisinfectTaskRow task(@NonNull DisinfectTaskVO task) {
        return new DisinfectTaskRow(TYPE_TASK, task.getStatus(), null, task);
    }

    public int getType() {
        return type;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getBanner() {
        return banner;
    }

    @Nullable
    public DisinfectTaskVO getTask() {
        return task;
    }

    /** 列表中的唯一键 */
    @NonNull
    String key() {
        return type == TYPE_TASK && task != null ? "T" + task.getId() : type + ":" + status + ":" + banner;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DisinfectTaskRow)) {
            return false;
        }
        DisinfectTaskRow r = (DisinfectTaskRow) o;
        return type == r.type && Objects.equals(status, r.status) && Objects.equals(banner, r.banner)
                && (task == null ? r.task == null : r.task != null && task.getId() == r.task.getId()
                && Objects.equals(task.getStatus(), r.task.getStatus()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, status, banner, task == null ? null : task.getId());
    }
}
