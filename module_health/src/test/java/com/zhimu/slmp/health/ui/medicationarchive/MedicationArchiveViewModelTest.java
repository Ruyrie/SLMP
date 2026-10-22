package com.zhimu.slmp.health.ui.medicationarchive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.health.TestRules;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.MedicationRepository;
import com.zhimu.slmp.health.model.vo.DeleteImpactVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.WithdrawalVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * D05 删除用药记录：按删除影响与角色分派对话框（四种 impact），申请原因 1–200 字校验，校验通过后 U01 → U02（change_type DELETE）。
 */
public class MedicationArchiveViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String RECORD = "{'id':5101,'version':2,'batch_id':3001,'drug_name':'氟苯尼考粉',"
            + "'first_date':'2026-10-05','last_date':'2026-10-09','actions':['EDIT','VIEW_LOG','DELETE']}";
    private static final String WITHDRAWAL = "{'batch_id':3001,'batch_no':'ZM2610-031','status':'IN_WITHDRAWAL',"
            + "'release_date':'2026-10-29','days_left':10,'drugs':[]}";

    private MedicationRepository repository;
    private HealthSharedRepository shared;
    private FarmContext farm;
    private MedicationArchiveViewModel vm;
    private MedicationRecordVO record;

    @Before
    public void setUp() {
        repository = mock(MedicationRepository.class);
        shared = mock(HealthSharedRepository.class);
        farm = mock(FarmContext.class);
        when(repository.getWithdrawal(3001L)).thenReturn(Single.just(TestRules.parse(WITHDRAWAL, WithdrawalVO.class)));
        record = TestRules.parse(RECORD, MedicationRecordVO.class);
    }

    private void open(boolean staff) {
        when(farm.isStaff()).thenReturn(staff);
        when(farm.isOwnerOrManager()).thenReturn(!staff);
        vm = new MedicationArchiveViewModel(repository, shared, farm);
        Map<String, Object> args = new HashMap<>();
        args.put(RouterPath.EXTRA_BATCH_ID, 3001L);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    private DeleteImpactVO impact(String kind) {
        return TestRules.parse("{'impact':'" + kind + "','record_title':'氟苯尼考粉 · 10月5日–10月9日',"
                + "'release_date_before':'2026-10-29','release_date_after':'2026-10-19','notify_name':'刘芳',"
                + "'notify_role':'MANAGER'}", DeleteImpactVO.class);
    }

    @Test
    public void ownerNoChangeTodayDeletesWithPlainConfirm() {
        open(false);
        DeleteImpactVO i = impact("NO_CHANGE_TODAY");
        vm.dispatchDelete(i);
        assertEquals(i, vm.getShowDeleteConfirm().getValue());
        assertNull(vm.getShowDeleteRequest().getValue());
        assertEquals("删除后可出栏日期不变，仍为 10月29日 周四。", vm.deleteConfirmMessage(i));
    }

    @Test
    public void ownerOtherImpactsNeedChangeRequest() {
        open(false);
        for (String kind : new String[]{"ADVANCE", "NO_CHANGE_EARLIER", "LOCKED"}) {
            DeleteImpactVO i = impact(kind);
            vm.dispatchDelete(i);
            assertEquals(i, vm.getShowDeleteRequest().getValue());
        }
        assertEquals("这项修改会使可出栏日期提前（10月29日 → 10月19日），需要提交修改申请，由管理员审核。审核通过前，记录和可出栏日期保持不变。",
                MedicationArchiveViewModel.deleteRequestMessage(impact("ADVANCE")));
        assertTrue(MedicationArchiveViewModel.deleteRequestMessage(impact("NO_CHANGE_EARLIER")).startsWith("删除会影响库存和批次成本"));
    }

    @Test
    public void staffCanOnlyNotifyManager() {
        open(true);
        DeleteImpactVO i = impact("ADVANCE");
        vm.dispatchDelete(i);
        assertEquals(i, vm.getShowStaffRestricted().getValue());
        assertNull(vm.getShowDeleteRequest().getValue());
        assertEquals("删除会使可出栏日期提前（10月29日 → 10月19日），需要由所有者或场长提交修改申请。你可以通知场长刘芳处理。",
                MedicationArchiveViewModel.staffRestrictedMessage(i));
    }

    @Test
    public void deleteReasonIsRequiredAndAtMost200() {
        open(false);
        when(repository.getDeleteImpact(5101L)).thenReturn(Single.just(impact("ADVANCE")));
        vm.requestDelete(record);
        assertEquals("请填写申请原因", vm.submitDeleteRequest("   "));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            tooLong.append('字');
        }
        assertEquals("申请原因最多 200 字", vm.submitDeleteRequest(tooLong.toString()));
        verify(shared, never()).getChangeRequestForm(anyString(), anyLong());
    }

    @Test
    public void validReasonSubmitsDeleteChangeRequest() {
        open(false);
        when(repository.getDeleteImpact(5101L)).thenReturn(Single.just(impact("ADVANCE")));
        ChangeRequestFormVO form = TestRules.parse("{'record_version':'2'}", ChangeRequestFormVO.class);
        when(shared.getChangeRequestForm("MEDICATION_RECORD", 5101L)).thenReturn(Single.just(form));
        when(shared.submitChangeRequest(any(ChangeRequestSubmitRequest.class), anyString()))
                .thenReturn(Single.just(TestRules.parse("{'id':9011,'status':'PENDING'}", ChangeRequestVO.class)));
        vm.requestDelete(record);
        assertNull(vm.submitDeleteRequest("登记错栏舍，实际用于 4 号舍"));
        ArgumentCaptor<ChangeRequestSubmitRequest> captor = ArgumentCaptor.forClass(ChangeRequestSubmitRequest.class);
        verify(shared).submitChangeRequest(captor.capture(), anyString());
        assertEquals("DELETE", captor.getValue().getChangeType());
        assertEquals(5101L, captor.getValue().getBizId());
        assertEquals("2", captor.getValue().getRecordVersion());
        assertEquals("登记错栏舍，实际用于 4 号舍", captor.getValue().getReason());
        assertEquals(Boolean.FALSE, vm.getSubmitting().getValue());
        verify(repository, never()).delete(eq(5101L), eq(2));
    }
}
