package com.zhimu.slmp.account.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.common.ui.change.ChangeRepository;
import com.zhimu.slmp.common.ui.change.ChangeRequestFormViewModel;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * U01 ChangeRequestFormViewModel：修改项、新值、作废与修改原因的校验，提交请求体与幂等键。
 */
public class ChangeRequestFormViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private ChangeRepository repository;
    private ChangeRequestFormViewModel vm;

    @Before
    public void setUp() {
        repository = mock(ChangeRepository.class);
        when(repository.getForm("FEED_RECORD", 7001L))
                .thenReturn(Single.just(TestData.fromJson(TestData.FEED_FORM, ChangeRequestFormVO.class)));
        vm = new ChangeRequestFormViewModel(repository);
        vm.getCanSubmit().observeForever(v -> {
        });
        vm.init("FEED_RECORD", 7001L, null);
    }

    @Test
    public void loadsForm() {
        assertNotNull(vm.getForm().getValue());
        assertTrue(vm.getCanSubmit().getValue());
        assertEquals(2, vm.getForm().getValue().getFields().size());
        verify(repository, never()).submit(any(), anyString());
    }

    @Test
    public void deleteOnlyMedicationFormRequiresSelectionAndReasonButNoNewValue() {
        String json = "{\"biz_type\":\"MEDICATION_RECORD\",\"biz_id\":5101,\"record_version\":\"2\","
                + "\"fields\":[],\"delete_allowed\":true,\"void_allowed\":false}";
        when(repository.getForm("MEDICATION_RECORD", 5101L))
                .thenReturn(Single.just(TestData.fromJson(json, ChangeRequestFormVO.class)));
        when(repository.submit(any(), anyString()))
                .thenReturn(Single.just(TestData.fromJson(TestData.CHANGE_SUBMITTED, ChangeRequestVO.class)));
        vm = new ChangeRequestFormViewModel(repository);
        vm.init("MEDICATION_RECORD", 5101L, null);
        assertTrue(vm.getForm().getValue().isDeleteAllowed());
        assertNull(vm.selectedField.getValue());
        vm.selectField(ChangeRequestFormViewModel.DELETE_CODE);
        assertTrue(vm.isDeleteSelected());
        vm.submit();
        assertEquals(ChangeRequestFormViewModel.MSG_REASON_EMPTY, vm.getFieldErrors().getValue().get("reason"));
        assertNull(vm.getFieldErrors().getValue().get("new_value"));
        verify(repository, never()).submit(any(), anyString());
        vm.reason.setValue("登记有误，申请删除后重新登记");
        vm.submit();
        ArgumentCaptor<ChangeRequestSubmitRequest> req = ArgumentCaptor.forClass(ChangeRequestSubmitRequest.class);
        verify(repository).submit(req.capture(), anyString());
        assertEquals("DELETE", req.getValue().getChangeType());
        assertNull(req.getValue().getFieldCode());
        assertNull(req.getValue().getNewValue());
        assertEquals("2", req.getValue().getRecordVersion());
    }

    @Test
    public void disallowedDeleteIsRejectedBeforeNetwork() {
        vm.selectField(ChangeRequestFormViewModel.DELETE_CODE);
        vm.reason.setValue("登记有误");
        vm.submit();
        assertEquals("这条记录暂不能删除", vm.getFieldErrors().getValue().get("field_code"));
        verify(repository, never()).submit(any(), anyString());
    }

    @Test
    public void selectFieldDisplaysLabelsButSubmitsIdsAndRejectsDisabledOptions() {
        String json = "{\"biz_type\":\"FEED_RECORD\",\"biz_id\":7001,\"record_version\":\"3\",\"fields\":["
                + "{\"field_code\":\"executor_id\",\"field_name\":\"执行人\",\"input_type\":\"SELECT\",\"value\":\"11\","
                + "\"options\":[{\"value\":\"11\",\"label\":\"李明\"},{\"value\":\"12\",\"label\":\"张伟\"},"
                + "{\"value\":\"13\",\"label\":\"已离职\",\"disabled\":true}]}]}";
        when(repository.getForm("FEED_RECORD", 7001L)).thenReturn(Single.just(TestData.fromJson(json, ChangeRequestFormVO.class)));
        when(repository.submit(any(), anyString())).thenReturn(Single.just(TestData.fromJson(TestData.CHANGE_SUBMITTED, ChangeRequestVO.class)));
        vm.retry();
        assertEquals("李明", ChangeRequestFormViewModel.displayValue(vm.currentField(), "11"));
        vm.reason.setValue("执行人登记有误");
        for (String invalid : Arrays.asList("13", "张伟", "999")) {
            vm.newValue.setValue(invalid);
            vm.submit();
            assertEquals("请选择有效的新值", vm.getFieldErrors().getValue().get("new_value"));
        }
        verify(repository, never()).submit(any(), anyString());
        vm.newValue.setValue("12");
        vm.submit();
        ArgumentCaptor<ChangeRequestSubmitRequest> req = ArgumentCaptor.forClass(ChangeRequestSubmitRequest.class);
        verify(repository).submit(req.capture(), anyString());
        assertEquals("12", req.getValue().getNewValue());
    }

    @Test
    public void staleFieldAndForbiddenVoidAreRejectedBeforeNetwork() {
        vm.selectField("missing");
        vm.newValue.setValue("10");
        vm.reason.setValue("误填");
        vm.submit();
        assertEquals(ChangeRequestFormViewModel.MSG_PICK_FIELD, vm.getFieldErrors().getValue().get("field_code"));
        String noVoid = TestData.FEED_FORM.replace("\"void_allowed\":true", "\"void_allowed\":false");
        when(repository.getForm("FEED_RECORD", 7001L)).thenReturn(Single.just(TestData.fromJson(noVoid, ChangeRequestFormVO.class)));
        vm.retry();
        vm.selectField(ChangeRequestFormViewModel.VOID_CODE);
        vm.submit();
        assertEquals("这条记录暂不能作废", vm.getFieldErrors().getValue().get("field_code"));
        verify(repository, never()).submit(any(), anyString());
    }

    @Test
    public void noFieldSelected_showsPickError() {
        vm.reason.setValue("误填");
        vm.submit();
        Map<String, String> errors = vm.getFieldErrors().getValue();
        assertEquals(ChangeRequestFormViewModel.MSG_PICK_FIELD, errors.get(ChangeRequestFormViewModel.FIELD_FIELD_CODE));
        verify(repository, never()).submit(any(), anyString());
    }

    @Test
    public void newValue_requiredAndMustDifferFromOriginal() {
        vm.selectField("actual_qty");
        vm.reason.setValue("误填");
        assertEquals(ChangeRequestFormViewModel.MSG_NEW_VALUE_EMPTY,
                vm.validate().get(ChangeRequestFormViewModel.FIELD_NEW_VALUE));
        vm.newValue.setValue("140.0");
        assertEquals(ChangeRequestFormViewModel.MSG_SAME_VALUE,
                vm.validate().get(ChangeRequestFormViewModel.FIELD_NEW_VALUE));
        vm.newValue.setValue("1200");
        assertEquals("实际投喂量不能超过 1000 千克", vm.validate().get(ChangeRequestFormViewModel.FIELD_NEW_VALUE));
        vm.newValue.setValue("160");
        assertNull(vm.validate().get(ChangeRequestFormViewModel.FIELD_NEW_VALUE));
    }

    @Test
    public void reasonRequired() {
        vm.selectField("actual_qty");
        vm.newValue.setValue("160");
        vm.reason.setValue("   ");
        assertEquals(ChangeRequestFormViewModel.MSG_REASON_EMPTY,
                vm.validate().get(ChangeRequestFormViewModel.FIELD_REASON));
    }

    @Test
    public void voidRecord_skipsNewValue_andSubmitsVoid() {
        when(repository.submit(any(), anyString()))
                .thenReturn(Single.just(TestData.fromJson(TestData.CHANGE_SUBMITTED, ChangeRequestVO.class)));
        vm.selectField(ChangeRequestFormViewModel.VOID_CODE);
        assertTrue(vm.isVoidSelected());
        assertEquals(Arrays.asList("库存（退回饲料）", "成本", "统计"), vm.currentRecalcTags());
        vm.reason.setValue("10月18日 晚餐实际没有投喂，这条记录是误登记的。");

        vm.submit();

        ArgumentCaptor<ChangeRequestSubmitRequest> req = ArgumentCaptor.forClass(ChangeRequestSubmitRequest.class);
        verify(repository).submit(req.capture(), anyString());
        assertEquals(ChangeRequestSubmitRequest.CHANGE_VOID, req.getValue().getChangeType());
        assertNull(req.getValue().getFieldCode());
        assertNull(req.getValue().getNewValue());
        assertEquals("3", req.getValue().getRecordVersion());
        assertEquals(9009L, vm.getSubmitted().getValue().getId());
        assertFalse(vm.getSubmitting().getValue());
    }

    @Test
    public void updateSubmit_sendsFieldAndNewValue_networkErrorKeepsIdemKey() {
        when(repository.submit(any(), anyString())).thenReturn(Single.error(ApiException.network(null)));
        vm.selectField("actual_qty");
        vm.newValue.setValue(" 160 ");
        vm.reason.setValue("登记时把实际投喂量误填为 140 千克，当餐实际投喂 160 千克。");

        vm.submit();

        ArgumentCaptor<ChangeRequestSubmitRequest> req = ArgumentCaptor.forClass(ChangeRequestSubmitRequest.class);
        verify(repository).submit(req.capture(), anyString());
        assertEquals(ChangeRequestSubmitRequest.CHANGE_UPDATE, req.getValue().getChangeType());
        assertEquals("actual_qty", req.getValue().getFieldCode());
        assertEquals("160", req.getValue().getNewValue());
        assertNotNull(vm.getIdemKey());
        assertEquals(ChangeRequestFormViewModel.ACTION_RETRY, vm.getMessage().getValue().getActionId());
    }

    @Test
    public void pendingRequest_disablesSubmit() {
        when(repository.getForm("FEED_RECORD", 7002L))
                .thenReturn(Single.just(TestData.fromJson(TestData.FEED_FORM_PENDING, ChangeRequestFormVO.class)));
        ChangeRequestFormViewModel pending = new ChangeRequestFormViewModel(repository);
        pending.getCanSubmit().observeForever(v -> {
        });
        pending.init("FEED_RECORD", 7002L, null);
        assertTrue(pending.hasPending());
        assertFalse(pending.getCanSubmit().getValue());
        pending.submit();
        verify(repository, never()).submit(any(), anyString());
    }
}
