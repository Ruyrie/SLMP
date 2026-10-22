package com.zhimu.slmp.map.ui.pick;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.map.databinding.MapItemTipBinding;
import com.zhimu.slmp.map.model.vo.AddressTipVO;

/**
 * F04 搜索联想结果：每条 56 dp，location_on + 地名 + 所在区县。
 */
public class TipAdapter extends ListAdapter<AddressTipVO, TipAdapter.Holder> {

    public interface OnTipClickListener {
        void onTipClick(@NonNull AddressTipVO tip);
    }

    private static final DiffUtil.ItemCallback<AddressTipVO> DIFF = new DiffUtil.ItemCallback<AddressTipVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull AddressTipVO a, @NonNull AddressTipVO b) {
            return a.equals(b);
        }

        @Override
        public boolean areContentsTheSame(@NonNull AddressTipVO a, @NonNull AddressTipVO b) {
            return a.equals(b);
        }
    };

    private final OnTipClickListener listener;

    public TipAdapter(@NonNull OnTipClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(MapItemTipBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AddressTipVO tip = getItem(position);
        holder.binding.mapTipName.setText(tip.getName());
        holder.binding.mapTipDistrict.setText(tip.getDistrict());
        holder.binding.mapTipDistrict.setVisibility(TextUtils.isEmpty(tip.getDistrict()) ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> listener.onTipClick(tip));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MapItemTipBinding binding;

        Holder(@NonNull MapItemTipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
