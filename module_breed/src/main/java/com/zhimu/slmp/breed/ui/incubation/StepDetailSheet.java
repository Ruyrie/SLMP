package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedIncludeKvBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

/**
 * B07 点已完成的步骤节点：底部面板只读显示该步登记内容与登记人、登记时间（待定事项 5：本步数值暂取详情顶层字段）。
 */
public class StepDetailSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_STEP = "step";
    private static final String[] STEP_CODES = {"SET", "CANDLE", "HATCH"};

    public static void open(@NonNull FragmentManager fm, int step, @NonNull CharSequence title) {
        StepDetailSheet s = new StepDetailSheet();
        Bundle args = baseArgs(null, title, null, null);
        args.putInt(ARG_STEP, step);
        s.setArguments(args);
        s.showSafely(fm);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        IncubationViewModel vm = new ViewModelProvider(requireActivity(), BreedInjector.factory())
                .get(IncubationViewModel.class);
        IncubationDetailVO d = vm.getDetail().getValue();
        int step = requireArguments().getInt(ARG_STEP, 1);
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_sheet_padding_horizontal);
        box.setPadding(pad, 0, pad, pad);
        if (d == null) {
            return box;
        }
        String egg = BreedTextFormatter.UNIT_EGG;
        String bird = BreedTextFormatter.UNIT_BIRD;
        if (step == 1) {
            row(inflater, box, getString(R.string.breed_field_set_date), BreedTextFormatter.fullDate(d.getSetDate()));
            row(inflater, box, getString(R.string.breed_field_flock), d.getFlockText());
            row(inflater, box, getString(R.string.breed_field_set_count), BreedTextFormatter.qty(d.getSetCount(), egg));
            row(inflater, box, getString(R.string.breed_hatch_card_label), BreedTextFormatter.fullDate(d.getExpectedHatchDate()));
        } else if (step == 2) {
            row(inflater, box, getString(R.string.breed_field_candle_date), BreedTextFormatter.fullDate(d.getCandleDate()));
            row(inflater, box, getString(R.string.breed_field_fertile), BreedTextFormatter.qty(d.getFertileCount(), egg));
            row(inflater, box, getString(R.string.breed_kv_fertility), BreedTextFormatter.percent(d.getFertilityRate()));
        } else {
            row(inflater, box, getString(R.string.breed_field_hatch_date), BreedTextFormatter.fullDate(d.getHatchDate()));
            row(inflater, box, getString(R.string.breed_field_hatched), BreedTextFormatter.qty(d.getHatchedCount(), bird));
            row(inflater, box, getString(R.string.breed_field_healthy), BreedTextFormatter.qty(d.getHealthyCount(), bird));
            row(inflater, box, getString(R.string.breed_field_weak), BreedTextFormatter.qty(d.getWeakCount(), bird));
            row(inflater, box, getString(R.string.breed_kv_hatch_rate), BreedTextFormatter.percent(d.getHatchRate()));
            row(inflater, box, getString(R.string.breed_kv_new_batch), d.getBatchNo() == null
                    ? getString(R.string.breed_kv_not_transferred) : d.getBatchNo());
        }
        for (IncubationDetailVO.TimelineItem t : d.getTimeline()) {
            if (STEP_CODES[Math.max(0, Math.min(2, step - 1))].equals(t.getStep())) {
                row(inflater, box, getString(R.string.breed_kv_registrant),
                        t.getRegistrant() == null ? "" : t.getRegistrant().getName());
                row(inflater, box, getString(R.string.breed_kv_registered_at),
                        DateFormatter.formatDateTime(t.getRegisteredAt()));
            }
        }
        return box;
    }

    private static void row(@NonNull LayoutInflater inflater, @NonNull LinearLayout box, @NonNull String label,
                            @Nullable String value) {
        BreedIncludeKvBinding b = BreedIncludeKvBinding.inflate(inflater, box, false);
        b.setLabel(label);
        b.setValue(value);
        b.executePendingBindings();
        box.addView(b.getRoot());
    }
}
