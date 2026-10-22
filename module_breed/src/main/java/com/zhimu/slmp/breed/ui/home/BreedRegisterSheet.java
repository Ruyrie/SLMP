package com.zhimu.slmp.breed.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.breed.databinding.BreedSheetRegisterBinding;
import com.zhimu.slmp.breed.model.BreedCodes;

/**
 * B01 FAB“登记”的底部面板“繁育登记”：选择后关闭面板并回传类型（MATING / FARROWING / WEANING / NEW_ANIMAL）。
 */
public class BreedRegisterSheet extends BaseBottomSheetDialogFragment {

    public static final String REQUEST_KEY = "breed_register_sheet";
    public static final String RESULT_TYPE = "type";

    public static void open(@NonNull FragmentManager fm, @NonNull CharSequence title, @Nullable CharSequence desc) {
        BreedRegisterSheet s = new BreedRegisterSheet();
        s.setArguments(baseArgs(REQUEST_KEY, title, desc, null));
        s.showSafely(fm);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        BreedSheetRegisterBinding b = BreedSheetRegisterBinding.inflate(inflater, container, false);
        ClickThrottle.setOnClickListener(b.breedRegisterMating, v -> pick(BreedCodes.TYPE_MATING));
        ClickThrottle.setOnClickListener(b.breedRegisterFarrowing, v -> pick(BreedCodes.TYPE_FARROWING));
        ClickThrottle.setOnClickListener(b.breedRegisterWeaning, v -> pick(BreedCodes.TYPE_WEANING));
        ClickThrottle.setOnClickListener(b.breedRegisterNew, v -> pick(BreedHomeViewModel.PAGE_NEW_ANIMAL));
        return b.getRoot();
    }

    private void pick(@NonNull String type) {
        Bundle r = new Bundle();
        r.putString(RESULT_TYPE, type);
        setResultAndDismiss(r);
    }
}
