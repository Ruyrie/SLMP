package com.zhimu.slmp.breed.ui.mating;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.model.vo.PendingCheckVO;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.RowBinder;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.function.Consumer;

/**
 * B03 待妊检列表：前置 female（secondary-container）、“0155 · 长大二元”、“9月24日配种 · 已 25 天”、尾部“登记妊检”。
 */
public class PendingCheckAdapter extends ListAdapter<PendingCheckVO, PendingCheckAdapter.Holder> {

    private final Consumer<PendingCheckVO> onCheck;

    public PendingCheckAdapter(@NonNull Consumer<PendingCheckVO> onCheck) {
        super(new DiffUtil.ItemCallback<PendingCheckVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull PendingCheckVO a, @NonNull PendingCheckVO b) {
                return a.getAnimalId() == b.getAnimalId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull PendingCheckVO a, @NonNull PendingCheckVO b) {
                return a.getCycleId() == b.getCycleId() && a.getDaysSinceMating() == b.getDaysSinceMating();
            }
        });
        this.onCheck = onCheck;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(BreedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PendingCheckVO item = getItem(position);
        BreedItemRowBinding b = holder.binding;
        RowBinder.reset(b, position, getItemCount());
        RowBinder.icon(b, R.drawable.breed_ic_female, BreedUi.TONE_SECONDARY);
        RowBinder.text(b, BreedTextFormatter.animalTitle(item.getEarTag(), item.getBreedName()),
                BreedTextFormatter.md(item.getFirstMatingDate()) + "配种" + BreedTextFormatter.DOT + "已 "
                        + item.getDaysSinceMating() + " 天", null);
        RowBinder.action(b, b.getRoot().getContext().getString(R.string.breed_action_register_check),
                v -> onCheck.accept(item));
    }

    @Override
    public void onCurrentListChanged(@NonNull java.util.List<PendingCheckVO> previous,
                                     @NonNull java.util.List<PendingCheckVO> current) {
        // 分组首末圆角随数量变化
        notifyItemRangeChanged(0, current.size());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final BreedItemRowBinding binding;

        Holder(@NonNull BreedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
