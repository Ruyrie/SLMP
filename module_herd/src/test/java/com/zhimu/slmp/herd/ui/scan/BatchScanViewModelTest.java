package com.zhimu.slmp.herd.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.util.BatchNoValidator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.reactivex.rxjava3.core.Single;

/**
 * L07 扫码结果分发：OK、SWITCH_FARM（先切换养殖场）、NO_PERMISSION、NOT_BATCH_QR、输入批次号的 NOT_FOUND 与本地格式校验、
 * 2 秒内相同内容忽略。
 */
public class BatchScanViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String QR = "https://web.zhimutong.example.com/s/b/k7Rm2xQ9pT";

    private BatchRepository repo;
    private IFarmContextService farmService;
    private BatchScanViewModel vm;

    @Before
    public void setUp() {
        repo = mock(BatchRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.current()).thenReturn(TestRules.parse("{'farm_id':1001,'name':'东山猪场','role':'OWNER'}",
                FarmContextVO.class));
        farmService = mock(IFarmContextService.class);
        ServiceHub.register(IFarmContextService.class, farmService);
        vm = new BatchScanViewModel(repo, farm);
    }

    private static BatchLookupVO lookup(String json) {
        return TestRules.parse(json, BatchLookupVO.class);
    }

    @Test
    public void ok_opensArchive() {
        when(repo.lookupByQr(QR)).thenReturn(Single.just(lookup("{'result':'OK','batch_id':3001,'farm_id':1001}")));

        vm.onQrDecoded(QR);

        assertEquals(Long.valueOf(3001L), vm.getOpenArchiveEvent().getValue().getBatchId());
        assertNull(vm.getSwitchedFromFarmId());
        assertFalse(vm.getLookupLoading().getValue());
    }

    @Test
    public void switchFarm_switchesBeforeOpening() {
        when(repo.lookupByQr(QR)).thenReturn(Single.just(lookup(
                "{'result':'SWITCH_FARM','batch_id':3201,'farm_id':1002,'farm_name':'西岭鸡场','message':'已切换到西岭鸡场'}")));
        when(farmService.switchTo(1002L)).thenReturn(Single.just(TestRules.parse(
                "{'farm_id':1002,'name':'西岭鸡场','role':'MANAGER'}", FarmContextVO.class)));

        vm.onQrDecoded(QR);

        verify(farmService).switchTo(1002L);
        assertEquals(Long.valueOf(3201L), vm.getOpenArchiveEvent().getValue().getBatchId());
        assertEquals(Long.valueOf(1001L), vm.getSwitchedFromFarmId());
        assertEquals("东山猪场", vm.getSwitchedFromFarmName());
    }

    @Test
    public void noPermission_showsDialogOnly() {
        when(repo.lookupByQr(QR)).thenReturn(Single.just(lookup(
                "{'result':'NO_PERMISSION','message':'你没有这个批次的查看权限。'}")));

        vm.onQrDecoded(QR);

        assertEquals("你没有这个批次的查看权限。", vm.getNoPermissionEvent().getValue());
        assertNull(vm.getOpenArchiveEvent().getValue());
        assertFalse(vm.getAnalyzing().getValue());
    }

    @Test
    public void notBatchQr_showsSnackbarMessage() {
        when(repo.lookupByQr(anyString())).thenReturn(Single.just(lookup(
                "{'result':'NOT_BATCH_QR','message':'没有识别到批次二维码，请对准批次标签重试'}")));

        vm.onQrDecoded("WIFI:S:farm;T:WPA;P:12345678;;");

        assertEquals("没有识别到批次二维码，请对准批次标签重试", vm.getMessage().getValue().getText());
        assertNull(vm.getOpenArchiveEvent().getValue());
        assertNull(vm.getBatchNoError().getValue());
    }

    @Test
    public void sameContentWithinTwoSeconds_isIgnored() {
        when(repo.lookupByQr(QR)).thenReturn(Single.just(lookup(
                "{'result':'NOT_FOUND','message':'没有找到这个批次，请核对批次标签'}")));

        vm.onQrDecoded(QR);
        vm.onQrDecoded(QR);

        verify(repo, times(1)).lookupByQr(QR);
    }

    @Test
    public void inputNotFound_writesInputError() {
        when(repo.lookupByNo("ZM2610-099")).thenReturn(Single.just(lookup(
                "{'result':'NOT_FOUND','message':'没有找到批次 ZM2610-099，请核对批次号'}")));
        vm.onBatchNoChanged(" zm2610-099 ");

        vm.submitBatchNo();

        verify(repo).lookupByNo("ZM2610-099");
        assertEquals("没有找到批次 ZM2610-099，请核对批次号", vm.getBatchNoError().getValue());
        assertNull(vm.getOpenArchiveEvent().getValue());
    }

    @Test
    public void inputInvalidFormat_rejectedLocally() {
        vm.onBatchNoChanged("ZM26-1");

        vm.submitBatchNo();

        assertEquals(BatchNoValidator.MSG_INVALID, vm.getBatchNoError().getValue());
        verify(repo, never()).lookupByNo(anyString());
    }
}
