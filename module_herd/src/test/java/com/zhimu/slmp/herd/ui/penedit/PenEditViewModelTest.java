package com.zhimu.slmp.herd.ui.penedit;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import io.reactivex.rxjava3.core.Single;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** L05 容量单位：选了品类按品类；新增未选品类时按当前养殖场主品类（鸡场“只”），取不到时“头”。 */
public class PenEditViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules schedulers = new TestRules();

    private static final String OPTIONS = "{'categories':["
            + "{'category_id':21,'category_name':'蛋鸡','count_unit':'BIRD','count_unit_text':'只'},"
            + "{'category_id':22,'category_name':'肉鸡','count_unit':'BIRD','count_unit_text':'只'}]}";

    private static PenEditViewModel vm(String options, String farm) {
        PenRepository repository = mock(PenRepository.class);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        FarmContext farmContext = mock(FarmContext.class);
        when(repository.getFormOptions()).thenReturn(Single.just(TestRules.parse(options, PenFormOptionsVO.class)));
        when(baseData.getDicts(anyString(), any())).thenReturn(Single.just(Collections.emptyList()));
        when(farmContext.current()).thenReturn(farm == null ? null : TestRules.parse(farm, FarmContextVO.class));
        PenEditViewModel vm = new PenEditViewModel(repository, baseData, farmContext);
        vm.init(null);
        return vm;
    }

    @Test public void newPenOnPoultryFarmUsesFarmMainCategoryUnitBeforeCategoryPicked() {
        PenEditViewModel vm = vm(OPTIONS, "{'farm_id':2,'main_category_id':21,'count_unit':'BIRD'}");
        assertEquals("只", vm.computeUnit());
    }

    @Test public void newPenFallsBackToFarmCountUnitWhenMainCategoryNotInOptions() {
        PenEditViewModel vm = vm("{'categories':[]}", "{'farm_id':2,'count_unit':'BIRD'}");
        assertEquals("只", vm.computeUnit());
    }

    @Test public void pickedCategoryWinsAndDefaultIsHead() {
        PenEditViewModel vm = vm(OPTIONS, null);
        assertEquals("头", vm.computeUnit());
        vm.getCategoryId().setValue(22L);
        assertEquals("只", vm.computeUnit());
    }
}
