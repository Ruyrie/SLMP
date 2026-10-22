package com.zhimu.slmp.breed.ui.due;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;

import java.util.Objects;

/**
 * 待产 / 待出雏列表的本地行模型：分组标题（group 变化处由 insertSeparators 插入）或待产项、待出雏项。
 */
public final class DueRow {

    @NonNull
    private final String group;
    @Nullable
    private final String groupText;
    @Nullable
    private final FarrowDueItemVO farrow;
    @Nullable
    private final HatchDueItemVO hatch;

    private DueRow(@NonNull String group, @Nullable String groupText, @Nullable FarrowDueItemVO farrow,
                   @Nullable HatchDueItemVO hatch) {
        this.group = group;
        this.groupText = groupText;
        this.farrow = farrow;
        this.hatch = hatch;
    }

    @NonNull
    public static DueRow header(@NonNull String group, @Nullable String groupText) {
        return new DueRow(group, groupText, null, null);
    }

    @NonNull
    public static DueRow of(@NonNull FarrowDueItemVO item) {
        return new DueRow(nz(item.getGroup()), item.getGroupText(), item, null);
    }

    @NonNull
    public static DueRow of(@NonNull HatchDueItemVO item) {
        return new DueRow(nz(item.getGroup()), item.getGroupText(), null, item);
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    public boolean isHeader() {
        return farrow == null && hatch == null;
    }

    @NonNull
    public String getGroup() {
        return group;
    }

    @Nullable
    public String getGroupText() {
        return groupText;
    }

    @Nullable
    public FarrowDueItemVO getFarrow() {
        return farrow;
    }

    @Nullable
    public HatchDueItemVO getHatch() {
        return hatch;
    }

    /** DiffUtil 用的稳定键 */
    @NonNull
    public String key() {
        if (farrow != null) {
            return "A" + farrow.getAnimalId();
        }
        if (hatch != null) {
            return "H" + hatch.getIncubationId();
        }
        return "G" + group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DueRow)) {
            return false;
        }
        DueRow r = (DueRow) o;
        return key().equals(r.key()) && Objects.equals(groupText, r.groupText);
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
