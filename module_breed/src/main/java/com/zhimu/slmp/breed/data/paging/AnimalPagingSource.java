package com.zhimu.slmp.breed.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.data.BreedApi;
import com.zhimu.slmp.breed.model.request.BreedingAnimalQuery;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * API-B02 种畜列表分页（B01），每页 20 条；第 1 页回调 tab_counts。
 */
public class AnimalPagingSource extends PagedSource<BreedingAnimalItemVO> {

    public AnimalPagingSource(@NonNull BreedApi api, @NonNull BreedingAnimalQuery query,
                              @Nullable Consumer<PageVO<BreedingAnimalItemVO>> onFirstPage) {
        super((page, size) -> api.getAnimals(query.getStatus(), query.getKeyword(), page, size)
                .compose(RxResult.unwrap()), onFirstPage);
    }
}
