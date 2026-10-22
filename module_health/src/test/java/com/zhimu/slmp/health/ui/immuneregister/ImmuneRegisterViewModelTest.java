package com.zhimu.slmp.health.ui.immuneregister;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.TestRules;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.model.request.ImmuneRecordCreateRequest;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanOptionVO;
import com.zhimu.slmp.health.model.vo.ImmuneRecordVO;
import com.zhimu.slmp.health.model.vo.ImmuneRegisterFormVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * D03 登记接种：按 API-D12 预填、批号默认先到期先出（2025081）、接种数量不超过存栏 200 头、
 * 所选批号结存不足的提示、员工不提交 executor_id、养殖户带默认执行人。
 */
public class ImmuneRegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String OPTION = "{'plan_id':8117,'target_type':'BATCH','batch_id':3001,'batch_no':'ZM2610-031',"
            + "'pen_name':'3 号育肥舍','vaccine_name':'猪瘟活疫苗','immune_round':'加强免疫','current_count':200,"
            + "'plan_date':'2026-10-19','plan_date_text':'今天','status':'PENDING'}";
    private static final String FORM = "{'plan':" + OPTION + ",'drug_id':5001,'vaccine_name':'猪瘟活疫苗',"
            + "'template_dose':1,'dose':1,'dose_unit':'头份 / 头','quantity_default':200,'current_count':200,"
            + "'method_code':'IM','backfill':false,'date_default':'2026-10-19','date_min':'2026-10-01',"
            + "'date_max':'2026-10-19','default_executor_id':10003,'creator_name':'王建国',"
            + "'default_lot':{'lot_id':6001,'lot_no':'2025081','expire_date':'2026-12-30','stock':400,'priority_out':true}}";
    private static final String LOTS = "{'default_lot_id':6001,'selectable':["
            + "{'lot_id':6001,'lot_no':'2025081','expire_date':'2026-12-30','lot_status':'NORMAL','stock':400,'priority_out':true},"
            + "{'lot_id':6002,'lot_no':'2025113','expire_date':'2027-03-15','lot_status':'NORMAL','stock':150}],"
            + "'unselectable':[{'lot_id':6003,'lot_no':'2025042','expire_date':'2026-10-12','lot_status':'EXPIRED'}]}";
    private static final String VACCINES = "[{'drug_id':5001,'catalog_id':7001,'name':'猪瘟活疫苗','dose_unit':'头份 / 头',"
            + "'stock_unit':'头份','available_qty':1000,'no_stock':false,'default_method_code':'IM'}]";
    private static final String RECORD = "{'id':8801,'plan_id':8117,'drug_lot_id':6001,'version':1,'quantity':200,"
            + "'dose':1,'total_dose':200,'todo_completed':true}";

    private ImmuneRepository repository;
    private HealthSharedRepository shared;
    private FarmContext farm;
    private ImmuneRegisterViewModel vm;

    @Before
    public void setUp() {
        repository = mock(ImmuneRepository.class);
        shared = mock(HealthSharedRepository.class);
        farm = mock(FarmContext.class);
        List<ImmunePlanOptionVO> options = Collections.singletonList(TestRules.parse(OPTION, ImmunePlanOptionVO.class));
        when(repository.getPlanOptions(isNull(), eq(1), eq(ImmuneRegisterViewModel.PLAN_PAGE_SIZE)))
                .thenReturn(Single.just(new PageVO<>(options, 1, 1, 50, false, null)));
        List<VaccineOptionVO> vaccines = TestRules.parse(VACCINES, new TypeToken<List<VaccineOptionVO>>() {
        }.getType());
        when(repository.getVaccineOptions(isNull())).thenReturn(Single.just(vaccines));
        when(repository.getRegisterForm(8117L, false)).thenReturn(Single.just(TestRules.parse(FORM, ImmuneRegisterFormVO.class)));
        when(shared.getVaccinateMethods(isNull())).thenReturn(Single.just(Collections.<DictItemVO>emptyList()));
        when(shared.getMembers()).thenReturn(Single.just(Collections.<MemberItemVO>emptyList()));
        when(shared.getLotOptions(eq(5001L), eq(HealthExtras.PURPOSE_VACCINATION), any()))
                .thenReturn(Single.just(TestRules.parse(LOTS, DrugLotOptionsVO.class)));
        when(repository.createRecord(any(ImmuneRecordCreateRequest.class), anyString()))
                .thenReturn(Single.just(TestRules.parse(RECORD, ImmuneRecordVO.class)));
    }

    private void open(boolean staff) {
        when(farm.isStaff()).thenReturn(staff);
        vm = new ImmuneRegisterViewModel(repository, shared, farm);
        Map<String, Object> args = new HashMap<>();
        args.put(HealthExtras.EXTRA_PLAN_ID, 8117L);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    @Test
    public void prefillsFromFormAndSelectsEarliestLot() {
        open(false);
        assertEquals(ImmuneRegisterViewModel.MODE_REGISTER, vm.getMode().getValue());
        assertEquals("1", vm.getDose().getValue());
        assertEquals("200", vm.getQuantity().getValue());
        assertEquals("IM", vm.getMethodCode().getValue());
        assertEquals(Long.valueOf(10003), vm.getExecutorId().getValue());
        assertEquals("2025081", vm.getSelectedLot().getValue().getLotNo());
        assertEquals(Boolean.TRUE, vm.isDefaultLotSelected());
        assertNull(vm.getLotError().getValue());
        // 派生值（MediatorLiveData）在有观察者时计算
        vm.getDeductText().observeForever(v -> {
        });
        assertEquals("登记后自动扣减批号 2025081 共 200 头份，对应待办自动完成。", TestRules.plain(vm.getDeductText().getValue()));
    }

    @Test
    public void quantityCannotExceedStock() {
        open(false);
        vm.getQuantity().setValue("201");
        vm.submit();
        assertEquals("不能超过存栏 200 头", TestRules.plain(vm.getFieldErrors().getValue().get(ImmuneRegisterViewModel.F_QUANTITY)));
        verify(repository, never()).createRecord(any(ImmuneRecordCreateRequest.class), anyString());
    }

    @Test
    public void unselectableLotIgnoredAndInsufficientLotBlocked() {
        open(false);
        vm.selectLot(6003);
        assertEquals("2025081", vm.getSelectedLot().getValue().getLotNo());
        vm.selectLot(6002);
        assertEquals("批号 2025113 结存 150 头份，不够本次 200 头份", TestRules.plain(vm.getLotError().getValue()));
        vm.submit();
        assertNotNull(vm.getFieldErrors().getValue().get(ImmuneRegisterViewModel.F_LOT));
        verify(repository, never()).createRecord(any(ImmuneRecordCreateRequest.class), anyString());
    }

    @Test
    public void staffSubmitWithoutExecutor() {
        open(true);
        vm.submit();
        ArgumentCaptor<ImmuneRecordCreateRequest> captor = ArgumentCaptor.forClass(ImmuneRecordCreateRequest.class);
        verify(repository).createRecord(captor.capture(), anyString());
        ImmuneRecordCreateRequest req = captor.getValue();
        assertNull(req.executorId);
        assertEquals(8117L, req.planId);
        assertEquals(6001L, req.drugLotId);
        assertEquals(200, req.quantity);
        assertEquals(0, new BigDecimal("1").compareTo(req.dose));
        assertEquals("已登记接种，待办已自动完成", vm.getFinishWithSuccess().getValue());
    }

    @Test
    public void ownerSubmitWithDefaultExecutor() {
        open(false);
        vm.submit();
        ArgumentCaptor<ImmuneRecordCreateRequest> captor = ArgumentCaptor.forClass(ImmuneRecordCreateRequest.class);
        verify(repository).createRecord(captor.capture(), anyString());
        assertEquals(Long.valueOf(10003), captor.getValue().executorId);
    }
}
