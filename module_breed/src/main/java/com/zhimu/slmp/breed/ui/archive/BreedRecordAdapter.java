package com.zhimu.slmp.breed.ui.archive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.RowBinder;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * B06 繁育记录（按时间倒序）：前置图标底统一 surface-container-highest；“断奶 / 10月12日 · 断奶仔猪 10 头”
 * “产仔 / 9月17日 · 总产 12 头 · 活仔 11 头 · 死胎 1 头 · 已转为新批次”“妊检 · 阳性 / 6月20日 · B 超”“配种 / 5月26日 · 人工授精”。
 * 有修改申请审核中时第三行显示“修改申请审核中”。点击在该项下方弹出记录菜单。
 */
public class BreedRecordAdapter extends PagingDataAdapter<BreedingRecordVO, BreedRecordAdapter.Holder> {

    private final BiConsumer<View, BreedingRecordVO> onClick;

    public BreedRecordAdapter(@NonNull BiConsumer<View, BreedingRecordVO> onClick) {
        super(new DiffUtil.ItemCallback<BreedingRecordVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull BreedingRecordVO a, @NonNull BreedingRecordVO b) {
                return a.getRecordId() == b.getRecordId() && Objects.equals(a.getRecordType(), b.getRecordType());
            }

            @Override
            public boolean areContentsTheSame(@NonNull BreedingRecordVO a, @NonNull BreedingRecordVO b) {
                return Objects.equals(subtitle(a), subtitle(b)) && Objects.equals(a.getVersion(), b.getVersion());
            }
        });
        this.onClick = onClick;
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    notifyItemChanged(positionStart - 1);
                }
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(BreedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BreedingRecordVO r = getItem(position);
        if (r == null) {
            return;
        }
        BreedItemRowBinding b = holder.binding;
        RowBinder.reset(b, position, getItemCount());
        RowBinder.icon(b, iconOf(r.getRecordType()), BreedUi.TONE_NEUTRAL);
        boolean pending = r.getChangeState() != null && r.getChangeState().getPendingRequestId() != null;
        RowBinder.text(b, title(r), subtitle(r),
                pending ? b.getRoot().getContext().getString(R.string.breed_record_pending) : null);
        b.breedRowSubtitle.setMaxLines(2);
        RowBinder.click(b, v -> onClick.accept(v, r));
    }

    @DrawableRes
    static int iconOf(String type) {
        if (BreedCodes.BIZ_WEANING.equals(type)) {
            return R.drawable.breed_ic_child_care;
        }
        if (BreedCodes.BIZ_FARROWING.equals(type)) {
            return com.zhimu.slmp.base.R.drawable.ic_add_box;
        }
        if (BreedCodes.BIZ_PREG_CHECK.equals(type)) {
            return com.zhimu.slmp.base.R.drawable.ic_fact_check;
        }
        if (BreedCodes.BIZ_BREEDING_EXIT.equals(type)) {
            return com.zhimu.slmp.base.R.drawable.ic_heart_minus;
        }
        return R.drawable.breed_ic_favorite;
    }

    @NonNull
    static String title(@NonNull BreedingRecordVO r) {
        if (BreedCodes.BIZ_PREG_CHECK.equals(r.getRecordType()) && r.getCheckResultText() != null) {
            return BreedTextFormatter.nz(r.getRecordTypeText()) + BreedTextFormatter.DOT + r.getCheckResultText();
        }
        if (BreedCodes.BIZ_BREEDING_EXIT.equals(r.getRecordType()) && r.getExitTypeText() != null) {
            return r.getExitTypeText();
        }
        return BreedTextFormatter.nz(r.getRecordTypeText());
    }

    @NonNull
    static String subtitle(@NonNull BreedingRecordVO r) {
        String dot = BreedTextFormatter.DOT;
        StringBuilder sb = new StringBuilder(BreedTextFormatter.md(r.getRecordDate()));
        String type = r.getRecordType() == null ? "" : r.getRecordType();
        switch (type) {
            case BreedCodes.BIZ_WEANING:
                if (r.getWeanedCount() != null) {
                    sb.append(dot).append("断奶仔猪 ").append(BreedTextFormatter.qty(r.getWeanedCount(),
                            BreedTextFormatter.UNIT_HEAD));
                }
                if (r.getLitterWeight() != null) {
                    sb.append(dot).append("窝重 ").append(NumberFormatter.formatQuantity(r.getLitterWeight(), 1, "千克"));
                }
                break;
            case BreedCodes.BIZ_FARROWING:
                appendCount(sb, "总产 ", r.getTotalBorn());
                appendCount(sb, "活仔 ", r.getLiveBorn());
                appendCount(sb, "死胎 ", r.getStillborn());
                if (r.getBatchId() != null) {
                    sb.append(dot).append("已转为新批次");
                }
                break;
            case BreedCodes.BIZ_PREG_CHECK:
                if (r.getCheckMethodText() != null) {
                    sb.append(dot).append(r.getCheckMethodText());
                }
                break;
            case BreedCodes.BIZ_MATING:
                if (r.getMatingMethodText() != null) {
                    sb.append(dot).append(r.getMatingMethodText());
                }
                if (r.getBoarInfo() != null && !r.getBoarInfo().isEmpty()) {
                    sb.append(dot).append("与配 ").append(r.getBoarInfo());
                }
                break;
            case BreedCodes.BIZ_BREEDING_EXIT:
                if (r.getExitReason() != null) {
                    sb.append(dot).append(r.getExitReason());
                }
                break;
            default:
                break;
        }
        return sb.toString();
    }

    private static void appendCount(@NonNull StringBuilder sb, @NonNull String label, Integer v) {
        if (v != null) {
            sb.append(BreedTextFormatter.DOT).append(label).append(BreedTextFormatter.qty(v, BreedTextFormatter.UNIT_HEAD));
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final BreedItemRowBinding binding;

        Holder(@NonNull BreedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
