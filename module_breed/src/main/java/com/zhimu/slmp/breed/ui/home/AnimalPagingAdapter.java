package com.zhimu.slmp.breed.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.RowBinder;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * B01 母猪列表（API-B02 分页）：前置 female（临产 warning、繁殖周期中 secondary、空怀 neutral）、“0131 · 长大二元”、
 * 按状态的说明（最多两行）、3 天内预产的临产标签“今天预产 / 还有 3 天”；尾部状态标签。
 */
public class AnimalPagingAdapter extends PagingDataAdapter<BreedingAnimalItemVO, AnimalPagingAdapter.Holder> {

    private final LongConsumer onClick;

    public AnimalPagingAdapter(@NonNull LongConsumer onClick) {
        super(new DiffUtil.ItemCallback<BreedingAnimalItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull BreedingAnimalItemVO a, @NonNull BreedingAnimalItemVO b) {
                return a.getAnimalId() == b.getAnimalId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull BreedingAnimalItemVO a, @NonNull BreedingAnimalItemVO b) {
                return a.getVersion() == b.getVersion() && Objects.equals(a.getStatus(), b.getStatus())
                        && Objects.equals(a.getDaysToDue(), b.getDaysToDue());
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
        BreedingAnimalItemVO a = getItem(position);
        if (a == null) {
            return;
        }
        BreedItemRowBinding b = holder.binding;
        RowBinder.reset(b, position, getItemCount());
        RowBinder.icon(b, R.drawable.breed_ic_female, BreedUi.animalTone(a.getStatus(), a.getDaysToDue()));
        RowBinder.text(b, BreedTextFormatter.animalTitle(a.getEarTag(), a.getBreedName()),
                BreedTextFormatter.animalSubtitle(a), null);
        if (BreedCodes.PREGNANT.equals(a.getStatus()) && BreedTextFormatter.isDueSoon(a.getDaysToDue())) {
            RowBinder.extraTag(b, BreedTextFormatter.dueTag(a.getDaysToDue()), StatusTagView.WARNING);
        }
        RowBinder.tag(b, a.getStatusText(), BreedUi.statusTagType(a.getStatus()));
        RowBinder.click(b, v -> onClick.accept(a.getAnimalId()));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final BreedItemRowBinding binding;

        Holder(@NonNull BreedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
