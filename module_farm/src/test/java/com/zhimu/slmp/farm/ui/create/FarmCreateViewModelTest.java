package com.zhimu.slmp.farm.ui.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.RxSchedulersRule;
import com.zhimu.slmp.farm.model.request.CreateFarmRequest;
import com.zhimu.slmp.farm.model.request.UpdateRegistrationRequest;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * FarmCreateViewModel：前端校验（字段键与文案）、提交（API-F05 / F07 请求体）、幂等键生成 / 沿用 / 重置、40905 写名称下。
 */
public class FarmCreateViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxSchedulersRule rx = new RxSchedulersRule();

    private FarmRepository repository;
    private UploadManager uploads;
    private MutableLiveData<List<UploadItem>> items;
    private FarmCreateViewModel vm;

    private final LocationResult location = new LocationResult(new BigDecimal("118.9123456"),
            new BigDecimal("42.3412345"), "内蒙古自治区", "赤峰市", "松山区", "太平地镇", "150404",
            "赤峰市松山区太平地镇 英金河北岸", LocationResult.SOURCE_DRAG, null);

    @Before
    public void setUp() {
        repository = mock(FarmRepository.class);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        SessionManager session = mock(SessionManager.class);
        AppSettings settings = mock(AppSettings.class);
        UploadManager.Factory factory = mock(UploadManager.Factory.class);
        uploads = mock(UploadManager.class);
        items = new MutableLiveData<>(Collections.emptyList());

        List<CategoryVO> categories = Arrays.asList(
                GsonFactory.get().fromJson("{\"id\":301,\"name\":\"肉牛\",\"count_unit\":\"HEAD\",\"count_unit_text\":\"头\"}", CategoryVO.class),
                GsonFactory.get().fromJson("{\"id\":201,\"name\":\"肉鸡\",\"count_unit\":\"BIRD\",\"count_unit_text\":\"只\"}", CategoryVO.class));
        when(baseData.getCategories()).thenReturn(Single.just(categories));
        when(session.currentUser()).thenReturn(new AccountUserVO(10001, "王建国", "王", "138****6620"));
        when(settings.getLastPhone()).thenReturn("13800006620");
        when(factory.create(any(), anyString(), anyString(), anyInt())).thenReturn(uploads);
        when(uploads.getItems()).thenReturn(items);
        when(uploads.current()).thenAnswer(inv -> items.getValue());
        when(uploads.hasUploading()).thenReturn(false);
        when(uploads.hasFailed()).thenReturn(false);
        when(uploads.getFileIds()).thenAnswer(inv -> items.getValue().isEmpty()
                ? Collections.<Long>emptyList() : Arrays.asList(9001L, 9002L));

        vm = new FarmCreateViewModel(repository, baseData, session, settings, factory);
    }

    private void attach(Map<String, Object> extras) {
        vm.attachSavedState(new SavedStateHandle(extras));
        // canSubmit 有观察者时字段变化才会重置幂等键、清除对应错误（页面中由 Activity 观察）
        vm.getCanSubmit().observeForever(v -> {
        });
        vm.getScaleUnit().observeForever(v -> {
        });
    }

    private void fillValidForm() {
        vm.getName().setValue("  北湾牛场 ");
        vm.getScale().setValue("120");
        vm.onCategorySelected(301);
        vm.onLocationPicked(location);
        items.setValue(Arrays.asList(UploadItem.remote(9001, "u1", "t1"), UploadItem.remote(9002, "u2", "t2")));
    }

    @Test
    public void createMode_prefillsContactFromSession() {
        attach(new HashMap<>());

        assertEquals("王建国", vm.getContactName().getValue());
        assertEquals("138 0000 6620", vm.getContactPhone().getValue());
        assertFalse(vm.isEdit());
    }

    @Test
    public void submit_emptyForm_showsFieldErrorsAndScrollsToFirst() {
        attach(new HashMap<>());

        vm.submit();

        Map<String, String> errors = vm.getFieldErrors().getValue();
        assertEquals(FarmRules.MSG_NAME_EMPTY, errors.get(FarmCreateViewModel.F_NAME));
        assertEquals(FarmRules.MSG_ADDRESS, errors.get(FarmCreateViewModel.F_ADDRESS));
        assertEquals(FarmRules.MSG_SCALE_EMPTY, errors.get(FarmCreateViewModel.F_SCALE));
        assertEquals(FarmRules.MSG_CATEGORY_EMPTY, errors.get(FarmCreateViewModel.F_CATEGORY));
        assertEquals(FarmRules.MSG_QUAL_EMPTY, errors.get(FarmCreateViewModel.F_QUAL));
        assertEquals(FarmCreateViewModel.F_NAME, vm.getScrollToField().getValue());
        verify(repository, never()).createFarm(any(), anyString());
    }

    @Test
    public void submit_invalidValues_reportsRuleErrors() {
        attach(new HashMap<>());
        fillValidForm();
        vm.getName().setValue("一二三四五六七八九十一二三四五六七八九十一");
        vm.getScale().setValue("0");
        vm.getContactPhone().setValue("1380000662");

        vm.submit();

        Map<String, String> errors = vm.getFieldErrors().getValue();
        assertEquals(FarmRules.MSG_NAME_TOO_LONG, errors.get(FarmCreateViewModel.F_NAME));
        assertEquals(FarmRules.MSG_SCALE_INVALID, errors.get(FarmCreateViewModel.F_SCALE));
        assertEquals(FarmRules.MSG_PHONE_INVALID, errors.get(FarmCreateViewModel.F_CONTACT_PHONE));
        verify(repository, never()).createFarm(any(), anyString());
    }

    @Test
    public void submit_valid_sendsRequestAndEmitsSubmitted() {
        attach(new HashMap<>());
        fillValidForm();
        when(repository.createFarm(any(), anyString())).thenReturn(Single.just(audit(1003)));

        vm.submit();

        ArgumentCaptor<CreateFarmRequest> req = ArgumentCaptor.forClass(CreateFarmRequest.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(repository).createFarm(req.capture(), key.capture());
        CreateFarmRequest r = req.getValue();
        assertEquals("北湾牛场", r.getName());
        assertEquals(120, r.getScale());
        assertEquals(301, r.getMainCategoryId());
        assertEquals("13800006620", r.getContactPhone());
        assertEquals("150404", r.getAdcode());
        assertEquals(new BigDecimal("118.9123456"), r.getLongitude());
        assertNull("拖动选点不上报定位精度", r.getLocAccuracy());
        assertEquals(Arrays.asList(9001L, 9002L), r.getQualificationFileIds());
        assertNotNull(key.getValue());
        assertEquals(Long.valueOf(1003), vm.getSubmitted().getValue());
        assertNull("成功后幂等键置空", vm.getIdemKey());
        assertFalse(vm.getSubmitting().getValue());
    }

    @Test
    public void idemKey_reusedOnRetry_resetAfterFieldChange() {
        attach(new HashMap<>());
        fillValidForm();
        when(repository.createFarm(any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new IOException("offline"))));

        vm.submit();
        String first = vm.getIdemKey();
        assertEquals(FarmRules.MSG_NETWORK_KEPT, vm.getMessage().getValue().getText());
        vm.submit();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).createFarm(any(), keys.capture());
        assertEquals("重试沿用同一幂等键", keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals(first, keys.getAllValues().get(1));

        vm.getScale().setValue("150");
        assertNull("改字段后幂等键置空", vm.getIdemKey());
        vm.submit();
        verify(repository, times(3)).createFarm(any(), keys.capture());
        assertNotEquals(first, keys.getValue());
    }

    @Test
    public void submit_whileUploading_doesNotSend() {
        attach(new HashMap<>());
        fillValidForm();
        when(uploads.hasUploading()).thenReturn(true);

        vm.submit();

        assertEquals(FarmRules.MSG_QUAL_UPLOADING, vm.getMessage().getValue().getText());
        verify(repository, never()).createFarm(any(), anyString());
    }

    @Test
    public void submit_nameDuplicate_writesErrorUnderName() {
        attach(new HashMap<>());
        fillValidForm();
        when(repository.createFarm(any(), anyString())).thenReturn(Single.error(
                new ApiException(ErrorCodes.NAME_DUPLICATE, "你已经有一个叫“北湾牛场”的养殖场，请换一个名称")));

        vm.submit();

        assertEquals("你已经有一个叫“北湾牛场”的养殖场，请换一个名称",
                vm.getFieldErrors().getValue().get(FarmCreateViewModel.F_NAME));
        assertEquals(FarmCreateViewModel.F_NAME, vm.getScrollToField().getValue());
        assertNull(vm.getSubmitted().getValue());
    }

    @Test
    public void canSubmit_onlyWhenRequiredFieldsFilled() {
        attach(new HashMap<>());
        assertFalse(vm.getCanSubmit().getValue());

        fillValidForm();

        assertTrue(vm.getCanSubmit().getValue());
        assertEquals("头", vm.getScaleUnit().getValue());
        vm.onCategorySelected(201);
        assertEquals("只", vm.getScaleUnit().getValue());
    }

    @Test
    public void editMode_prefillsAndResubmitsWithAuditVersion() {
        when(repository.getAudit(1003)).thenReturn(Single.just(GsonFactory.get().fromJson(
                "{\"farm_id\":1003,\"name\":\"北湾牛场\",\"status\":\"REJECTED\",\"audit_version\":2,"
                        + "\"registration\":{\"name\":\"北湾牛场\",\"main_category_id\":301,\"scale\":120,"
                        + "\"contact_name\":\"王建国\",\"contact_phone\":\"13800006620\","
                        + "\"address\":\"赤峰市松山区太平地镇 英金河北岸\",\"province\":\"内蒙古自治区\",\"city\":\"赤峰市\","
                        + "\"district\":\"松山区\",\"adcode\":\"150404\",\"longitude\":118.9123456,\"latitude\":42.3412345,"
                        + "\"qualification_files\":[{\"file_id\":9001},{\"file_id\":9002,\"reshoot\":true}]}}",
                FarmAuditVO.class)));
        when(repository.updateRegistration(anyLong(), any(), anyString())).thenReturn(Completable.complete());
        Map<String, Object> extras = new HashMap<>();
        extras.put(RouterPath.EXTRA_MODE, FarmExtras.MODE_EDIT);
        extras.put(RouterPath.EXTRA_FARM_ID, 1003L);
        attach(extras);
        items.setValue(Arrays.asList(UploadItem.remote(9001, "u1", "t1"), UploadItem.remote(9002, "u2", "t2")));

        assertTrue(vm.isEdit());
        assertEquals("北湾牛场", vm.getName().getValue());
        assertEquals("120", vm.getScale().getValue());
        assertEquals("138 0000 6620", vm.getContactPhone().getValue());
        assertEquals(2, vm.getAuditVersion());

        vm.submit();

        ArgumentCaptor<UpdateRegistrationRequest> req = ArgumentCaptor.forClass(UpdateRegistrationRequest.class);
        verify(repository).updateRegistration(eq(1003L), req.capture(), anyString());
        assertEquals(2, req.getValue().getAuditVersion());
        assertEquals(Long.valueOf(1003), vm.getSubmitted().getValue());
    }

    @Test
    public void backPressed_withContent_showsDiscard() {
        attach(new HashMap<>());
        assertFalse(vm.onBackPressed());

        vm.getName().setValue("北湾牛场");

        assertTrue(vm.onBackPressed());
    }

    private static FarmAuditVO audit(long farmId) {
        return GsonFactory.get().fromJson("{\"farm_id\":" + farmId + ",\"status\":\"PENDING\"}", FarmAuditVO.class);
    }
}
