package com.zhimu.slmp.farm.ui.switcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.databinding.FarmItemSwitchBinding;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.farm.util.CountUnitText;

import java.util.Objects;

/**
 * F01 养殖场单选列表：副文字由适配器拼接（UI 文档 F01、2.5 节）——“所有者 · 育肥猪 · 870 头”“员工 · 负责 2 个栏舍”，
 * 待审核、已驳回、已停用、已冻结见 {@link #subtitleOf(FarmListItemVO)}；切换中的行显示进度环，其余行不可点。
 */
public class FarmSwitchAdapter extends PagingDataAdapter<FarmListItemVO, FarmSwitchAdapter.Holder> {

    public interface OnFarmClickListener {
        void onFarmClick(@NonNull FarmListItemVO item);
    }

    private static final DiffUtil.ItemCallback<FarmListItemVO> DIFF = new DiffUtil.ItemCallback<FarmListItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull FarmListItemVO a, @NonNull FarmListItemVO b) {
            return a.getFarmId() == b.getFarmId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull FarmListItemVO a, @NonNull FarmListItemVO b) {
            return a.equals(b);
        }
    };

    private final OnFarmClickListener listener;
    /** 单选选中的养殖场（全部养殖场视角时为 -1） */
    private long checkedFarmId = -1;
    /** 当前养殖场（FarmContext；“当前”标签） */
    private long currentFarmId = -1;
    @Nullable
    private Long switchingFarmId;

    public FarmSwitchAdapter(@NonNull OnFarmClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setCheckedFarmId(long checked, long current) {
        if (checkedFarmId != checked || currentFarmId != current) {
            checkedFarmId = checked;
            currentFarmId = current;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    public void setSwitchingFarmId(@Nullable Long farmId) {
        if (!Objects.equals(switchingFarmId, farmId)) {
            switchingFarmId = farmId;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FarmItemSwitchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FarmListItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        FarmItemSwitchBinding b = holder.binding;
        b.farmItemName.setText(item.getName());
        b.farmItemSubtitle.setText(subtitleOf(item));
        boolean weak = isPendingAudit(item.getStatus());
        b.farmItemName.setTextColor(MaterialColors.getColor(b.farmItemName, weak
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface));
        b.farmItemRadio.setChecked(item.getFarmId() == checkedFarmId);

        boolean switching = switchingFarmId != null && switchingFarmId == item.getFarmId();
        b.farmItemProgress.setVisibility(switching ? View.VISIBLE : View.GONE);
        b.farmItemRadio.setVisibility(switching ? View.INVISIBLE : View.VISIBLE);
        boolean enabled = switchingFarmId == null || switching;
        holder.itemView.setEnabled(enabled);
        holder.itemView.setAlpha(enabled ? 1f : 0.6f);

        bindTag(b.farmItemTag, item, currentFarmId > 0 ? item.getFarmId() == currentFarmId : item.isCurrent());
        holder.itemView.setOnClickListener(v -> {
            if (switchingFarmId == null) {
                listener.onFarmClick(item);
            }
        });
    }

    private static void bindTag(@NonNull StatusTagView tag, @NonNull FarmListItemVO item, boolean current) {
        String status = item.getStatus();
        if (FarmContextVO.STATUS_PENDING.equals(status)) {
            show(tag, "待审核", StatusTagView.WARNING, 0);
        } else if (FarmContextVO.STATUS_REJECTED.equals(status)) {
            show(tag, "已驳回", StatusTagView.ERROR, com.zhimu.slmp.base.R.drawable.ic_block);
        } else if (FarmContextVO.STATUS_FROZEN.equals(status)) {
            show(tag, "已冻结", StatusTagView.ERROR, 0);
        } else if (current) {
            show(tag, "当前", StatusTagView.PRIMARY, 0);
        } else if (FarmContextVO.STATUS_DISABLED.equals(status)) {
            show(tag, "已停用", StatusTagView.ERROR, 0);
        } else {
            tag.setVisibility(View.GONE);
        }
    }

    private static void show(@NonNull StatusTagView tag, @NonNull String text, int type, int icon) {
        tag.setStatus(text, type);
        if (icon != 0) {
            tag.setTagIcon(icon);
        } else {
            tag.setTagIcon(null);
        }
        tag.setVisibility(View.VISIBLE);
    }

    /** 副文字：角色 · 主要品类 · 当前存栏；员工只显示负责栏舍数；其他状态按 UI 文档 2.5 节 */
    @NonNull
    public static String subtitleOf(@NonNull FarmListItemVO item) {
        String role = item.getRoleText() == null ? "" : item.getRoleText();
        String status = item.getStatus();
        if (FarmContextVO.STATUS_PENDING.equals(status)) {
            return role + " · 审核通过前只能修改注册信息";
        }
        if (FarmContextVO.STATUS_REJECTED.equals(status)) {
            return role + " · 已驳回，修改后可重新提交";
        }
        if (FarmContextVO.STATUS_FROZEN.equals(status)) {
            return "已被平台冻结，暂时不能使用";
        }
        StringBuilder sb = new StringBuilder(role);
        if (FarmContextVO.ROLE_STAFF.equals(item.getRole())) {
            int pens = item.getMyPenCount() == null ? 0 : item.getMyPenCount();
            sb.append(" · 负责 ").append(pens).append(" 个栏舍");
        } else {
            if (item.getMainCategoryName() != null) {
                sb.append(" · ").append(item.getMainCategoryName());
            }
            if (item.getCurrentStock() != null && !FarmContextVO.STATUS_DISABLED.equals(status)) {
                sb.append(" · ").append(NumberFormatter.formatQuantity(item.getCurrentStock(),
                        CountUnitText.of(item.getCountUnit(), item.getCountUnitText())));
            }
        }
        if (FarmContextVO.STATUS_DISABLED.equals(status)) {
            sb.append(" · 已停用，只能查看");
        }
        return sb.toString();
    }

    static boolean isPendingAudit(@Nullable String status) {
        return FarmContextVO.STATUS_PENDING.equals(status) || FarmContextVO.STATUS_REJECTED.equals(status);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FarmItemSwitchBinding binding;

        Holder(@NonNull FarmItemSwitchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
