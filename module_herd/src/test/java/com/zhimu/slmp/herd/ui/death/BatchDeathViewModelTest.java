package com.zhimu.slmp.herd.ui.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.DeathCreateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.DeathResultVO;
import com.zhimu.slmp.herd.model.vo.ExecutorOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.media.upload.UploadManager;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * L11 死淘登记校验：数量不超过栏舍在栏数、原因必选、“其他”时说明必填、照片上传中不提交；养殖户带实际执行人、员工不带。
 */
public class BatchDeathViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String BATCH = "{'batch_id':3001,'batch_no':'ZM2610-031','entry_date':'2026-10-01',"
            + "'current_count':200,'unit_text':'头'}";
    private static final String PEN_STOCKS = "[{'pen_id':2003,'pen_name':'3 号育肥舍','first_in_date':'2026-10-01',"
            + "'stock_count':200,'capacity':220}]";
    private static final String EXECUTORS = "[{'user_id':10001,'name':'王建国','display_name':'王建国（我）','is_me':true},"
            + "{'user_id':10003,'name':'李明','display_name':'李明','is_me':false}]";
    private static final String REASONS = "[{'code':'SICK','name':'病死'},{'code':'OTHER','name':'其他'}]";
    private static final String RESULT = "{'event_id':9101,'batch_id':3001,'quantity':10,'message':'已登记死淘 10 头，存栏已更新'}";

    private BatchRepository batchRepo;
    private PenRepository penRepo;
    private FarmContext farm;
    private UploadManager photos;
    private BatchDeathViewModel vm;

    @Before
    public void setUp() {
        batchRepo = mock(BatchRepository.class);
        penRepo = mock(PenRepository.class);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        farm = mock(FarmContext.class);
        AppDatabase db = mock(AppDatabase.class);
        FormDraftDao dao = mock(FormDraftDao.class);
        UploadManager.Factory uploads = mock(UploadManager.Factory.class);
        photos = mock(UploadManager.class);

        when(db.formDraftDao()).thenReturn(dao);
        when(dao.get(anyString(), anyLong(), anyString())).thenReturn(Maybe.empty());
        when(dao.delete(anyString(), anyLong(), anyString())).thenReturn(Completable.complete());
        when(uploads.create(any(SavedStateHandle.class), anyString(), anyString(), anyInt())).thenReturn(photos);
        when(photos.getFileIds()).thenReturn(Collections.emptyList());
        List<BatchListItemVO> batches = TestRules.parse("[" + BATCH + "]", new TypeToken<List<BatchListItemVO>>() {
        }.getType());
        when(batchRepo.getActiveBatches()).thenReturn(Single.just(new PageVO<>(batches, 1, 1, 100, false, null)));
        List<DictItemVO> reasons = TestRules.parse(REASONS, new TypeToken<List<DictItemVO>>() {
        }.getType());
        when(baseData.getDicts(eq(DictTypes.DEATH_REASON), isNull())).thenReturn(Single.just(reasons));
        when(batchRepo.getBatchDetail(3001L)).thenReturn(Single.just(TestRules.parse(BATCH, BatchDetailVO.class)));
        List<PenStockVO> pens = TestRules.parse(PEN_STOCKS, new TypeToken<List<PenStockVO>>() {
        }.getType());
        when(batchRepo.getPenStocks(3001L)).thenReturn(Single.just(pens));
        List<ExecutorOptionVO> executors = TestRules.parse(EXECUTORS, new TypeToken<List<ExecutorOptionVO>>() {
        }.getType());
        when(penRepo.getExecutors(2003L)).thenReturn(Single.just(executors));
        when(batchRepo.createDeath(any(DeathCreateRequest.class), anyString()))
                .thenReturn(Single.just(TestRules.parse(RESULT, DeathResultVO.class)));
        vm = new BatchDeathViewModel(batchRepo, penRepo, baseData, farm, db, uploads);
    }

    private void open(boolean staff) {
        when(farm.isStaff()).thenReturn(staff);
        Map<String, Object> args = new HashMap<>();
        args.put(RouterPath.EXTRA_BATCH_ID, 3001L);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    private Map<String, String> errors() {
        return vm.getFieldErrors().getValue();
    }

    @Test
    public void quantityOverPenStock_showsLimitAndDoesNotSubmit() {
        open(false);
        vm.getQuantityText().setValue("210");
        vm.onReasonSelected("SICK");

        vm.submit();

        assertEquals("不能超过 3 号育肥舍的在栏数 200 头", TestRules.plain(errors().get(BatchDeathViewModel.F_QUANTITY)));
        verify(batchRepo, never()).createDeath(any(DeathCreateRequest.class), anyString());
    }

    @Test
    public void liveQuantityCheck_errorThenRecovers() {
        open(false);
        vm.getQuantityText().setValue("210");
        vm.validateQuantity();
        assertEquals("不能超过 3 号育肥舍的在栏数 200 头", TestRules.plain(errors().get(BatchDeathViewModel.F_QUANTITY)));

        vm.getQuantityText().setValue("10");
        vm.validateQuantity();
        assertNull(TestRules.plain(errors().get(BatchDeathViewModel.F_QUANTITY)));
    }

    @Test
    public void reasonRequired_andOtherNeedsRemark() {
        open(false);
        vm.getQuantityText().setValue("10");

        vm.submit();
        assertEquals("请选择死淘原因", errors().get(BatchDeathViewModel.F_REASON));

        vm.onReasonSelected(BatchDeathViewModel.REASON_OTHER);
        vm.submit();
        assertNull(errors().get(BatchDeathViewModel.F_REASON));
        assertEquals("选择‘其他’时请写明原因", errors().get(BatchDeathViewModel.F_REMARK));
        verify(batchRepo, never()).createDeath(any(DeathCreateRequest.class), anyString());
    }

    @Test
    public void photosUploading_blocksSubmit() {
        open(false);
        when(photos.hasUploading()).thenReturn(true);
        vm.getQuantityText().setValue("10");
        vm.onReasonSelected("SICK");

        vm.submit();

        assertEquals(BatchDeathViewModel.MSG_PHOTO_UPLOADING, vm.getMessage().getValue().getText());
        verify(batchRepo, never()).createDeath(any(DeathCreateRequest.class), anyString());
    }

    @Test
    public void ownerSubmit_sendsExecutor() {
        open(false);
        vm.getQuantityText().setValue("10");
        vm.onReasonSelected("SICK");

        vm.submit();

        ArgumentCaptor<DeathCreateRequest> req = ArgumentCaptor.forClass(DeathCreateRequest.class);
        verify(batchRepo).createDeath(req.capture(), anyString());
        assertEquals(3001L, req.getValue().batchId);
        assertEquals(Long.valueOf(2003L), req.getValue().penId);
        assertEquals(10, req.getValue().quantity);
        assertEquals(Long.valueOf(10001L), req.getValue().executorUserId);
        assertEquals(Integer.valueOf(200), req.getValue().seenStockCount);
        assertEquals("已登记死淘 10 头，存栏已更新", vm.getFinishEvent().getValue().getText());
    }

    @Test
    public void staffSubmit_hidesAndOmitsExecutor() {
        open(true);
        vm.getQuantityText().setValue("10");
        vm.onReasonSelected("SICK");

        vm.submit();

        assertFalse(vm.getShowExecutor().getValue());
        verify(penRepo, never()).getExecutors(anyLong());
        ArgumentCaptor<DeathCreateRequest> req = ArgumentCaptor.forClass(DeathCreateRequest.class);
        verify(batchRepo).createDeath(req.capture(), anyString());
        assertNull(req.getValue().executorUserId);
    }
}
