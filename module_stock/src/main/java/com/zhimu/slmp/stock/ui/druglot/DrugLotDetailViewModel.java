package com.zhimu.slmp.stock.ui.druglot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.ui.common.GroupedPagedSource;
import com.zhimu.slmp.stock.ui.common.ListRow;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * M06 批号详情（技术准备文档 2.4.8）：API-M26 概要、批号信息与停用横幅；页内流水 API-M28（drug_lot_id）分页。
 * 底部：can_scrap “登记报废”、can_out “出库”（正常、临期批号）；结存为 0 时不显示。
 * 40401 / 41001：从消息进入（带 message_id）打开 T08，否则 U03 加载失败。
 */
public class DrugLotDetailViewModel extends BaseListViewModel<Long, ListRow<StockFlowItemVO>> {

    /** 停用横幅：级别、图标、粗体结论、正文 */
    public static final class Banner {
        public final int level;
        public final int icon;
        @NonNull
        public final String title;
        @NonNull
        public final String text;

        Banner(int level, int icon, @NonNull String title, @NonNull String text) {
            this.level = level;
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }

    private final StockRepository repository;

    private final MutableLiveData<DrugLotDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<Banner> banner = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showScrap = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showOut = new MutableLiveData<>(false);
    private final MutableLiveData<List<String[]>> infoRows = new MutableLiveData<>(new ArrayList<>());

    private final SingleLiveEvent<Long> openScrap = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openOutbound = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openInboundEdit = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> recordGone = new SingleLiveEvent<>();
    private final SingleLiveEvent<StockFlowItemVO> openRecord = new SingleLiveEvent<>();

    private long lotId;
    private boolean fromMessage;

    @Inject
    public DrugLotDetailViewModel(@NonNull StockRepository repository) {
        super(null);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = StockExtras.readId(handle.get(StockExtras.EXTRA_LOT_ID));
        if (id == null) {
            id = StockExtras.readId(handle.get(RouterPath.EXTRA_BIZ_ID));
        }
        fromMessage = handle.contains(RouterPath.EXTRA_MESSAGE_ID) || handle.contains(RouterPath.EXTRA_ROUTE);
        load(id == null ? 0 : id);
    }

    public void load(long id) {
        lotId = id;
        load(repository.getDrugLot(id), this::apply);
        setFilter(id);
    }

    @Override
    public void retry() {
        load(repository.getDrugLot(lotId), this::apply);
        refresh();
    }

    /** 下拉刷新与子页返回：重取详情与流水 */
    public void reload() {
        load(repository.getDrugLot(lotId), this::apply);
        refresh();
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.NOT_FOUND || e.getCode() == ErrorCodes.RECORD_DELETED) {
            if (fromMessage) {
                recordGone.setValue(e.getMessage());
                return;
            }
        }
        super.applyErrorState(e);
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ListRow<StockFlowItemVO>> createPagingSource(@Nullable Long id) {
        long lot = id == null ? 0 : id;
        return new GroupedPagedSource<>((page, size) -> repository.pageDrugFlows(lot, null, StockEnums.GROUP_ALL,
                page, size), null, f -> String.valueOf(f.flowId));
    }

    private void apply(@NonNull DrugLotDetailVO d) {
        detail.setValue(d);
        banner.setValue(bannerOf(d));
        boolean hasStock = d.stock != null && d.stock.value != null && d.stock.value.signum() > 0;
        StockActionsVO a = d.actions;
        boolean dead = StockEnums.LOT_RECALLED.equals(d.lotStatus) || StockEnums.LOT_EXPIRED.equals(d.lotStatus)
                || d.drugBanned;
        showScrap.setValue(hasStock && (a == null ? true : !Boolean.FALSE.equals(a.canScrap)));
        showOut.setValue(hasStock && !dead && (a == null || Boolean.TRUE.equals(a.canOut)));
        infoRows.setValue(infoRowsOf(d));
    }

    /** 已停用 / 已过期 / 临期 三种横幅；正常批号没有横幅 */
    @Nullable
    public static Banner bannerOf(@NonNull DrugLotDetailVO d) {
        String verb = StockTexts.useVerb(d.category);
        if (StockEnums.LOT_RECALLED.equals(d.lotStatus)) {
            String reason = d.recallReason == null || d.recallReason.isEmpty() ? "" : "原因是" + d.recallReason + "。";
            return new Banner(2, com.zhimu.slmp.base.R.drawable.ic_block, "该批号已被平台停用：",
                    reason + "该批号不能出库，登记" + verb + "时也选不到，只能报废。");
        }
        if (StockEnums.LOT_EXPIRED.equals(d.lotStatus)) {
            return new Banner(2, com.zhimu.slmp.stock.R.drawable.stock_ic_event_busy, "该批号已过期：",
                    "有效期至 " + StockTexts.nz(d.expireDate) + "。该批号不能出库，登记" + verb + "时也选不到，只能报废。");
        }
        if (StockEnums.LOT_NEAR_EXPIRY.equals(d.lotStatus)) {
            return new Banner(1, com.zhimu.slmp.base.R.drawable.ic_schedule, "该批号临期：",
                    "还有 " + (d.daysToExpire == null ? 0 : d.daysToExpire)
                            + " 天到期。登记接种、用药或消毒时会优先扣减这个批号。");
        }
        return null;
    }

    /** 批号信息：有效期至、入库、停用时间、停用通知（链接行，值为空串） */
    @NonNull
    static List<String[]> infoRowsOf(@NonNull DrugLotDetailVO d) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"有效期至", d.expireDate});
        DrugLotDetailVO.FirstInbound f = d.firstInbound;
        if (f != null) {
            String date = f.inboundDate == null ? null : DateFormatter.formatDate(f.inboundDate, false);
            String pack = d.stock != null && d.stock.packUnit != null ? d.stock.packUnit : f.qty == null ? ""
                    : StockQtyFormatter.unitText(f.qty);
            String price = f.unitPrice == null ? null : "每" + pack + " " + StockCalc.money(f.unitPrice) + " 元";
            rows.add(new String[]{"入库", StockTexts.join(StockTexts.DOT, date, StockQtyFormatter.packText(f.qty), price)});
        }
        if (StockEnums.LOT_RECALLED.equals(d.lotStatus)) {
            rows.add(new String[]{"停用时间", StockTexts.join(StockTexts.DOT,
                    d.recalledAt == null ? null : DateFormatter.formatDateTime(d.recalledAt), d.recallOperatorText)});
        }
        return rows;
    }

    /** 概要卡副标题“疫苗 · 批号 2025047” */
    @NonNull
    public static String subtitle(@NonNull DrugLotDetailVO d) {
        return StockTexts.join(StockTexts.DOT, d.categoryText != null ? d.categoryText
                : StockEnums.categoryText(d.category), "批号 " + StockTexts.nz(d.lotNo));
    }

    /** 库存金额“840 元” */
    @NonNull
    public static String amountText(@NonNull DrugLotDetailVO d) {
        java.math.BigDecimal v = d.stockAmount == null ? java.math.BigDecimal.ZERO : d.stockAmount;
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        return NumberFormatter.formatNumber(v, whole ? 0 : 2);
    }

    // ===================== 操作 =====================

    public void onScrap() {
        openScrap.setValue(lotId);
    }

    public void onOutbound() {
        openOutbound.setValue(lotId);
    }

    public void onArchive() {
        DrugLotDetailVO d = detail.getValue();
        if (d != null) {
            openArchive.setValue(d.drugId);
        }
    }

    public void onMessage() {
        DrugLotDetailVO d = detail.getValue();
        if (d != null && d.recallMessageId != null) {
            openMessage.setValue(d.recallMessageId);
        }
    }

    /** 菜单“修改入库信息”：首张入库单的 M08 修改状态 */
    public void onEditInbound() {
        DrugLotDetailVO d = detail.getValue();
        if (d != null && d.firstInbound != null) {
            openInboundEdit.setValue(d.firstInbound.inboundId);
        }
    }

    public void onRequestChange() {
        DrugLotDetailVO d = detail.getValue();
        if (d != null && d.firstInbound != null) {
            openChangeRequest.setValue(d.firstInbound.inboundId);
        }
    }

    public void onChangeLog() {
        DrugLotDetailVO d = detail.getValue();
        if (d != null && d.firstInbound != null) {
            openChangeLog.setValue(d.firstInbound.inboundId);
        }
    }

    /** 入库后还没有发生出库：可修改入库信息；否则提交修改申请 */
    public boolean canEditInbound() {
        DrugLotDetailVO d = detail.getValue();
        if (d == null || d.firstInbound == null) {
            return false;
        }
        StockActionsVO a = d.actions;
        if (a != null && a.editState != null) {
            return StockEnums.EDIT_EDITABLE.equals(a.editState) && a.canEdit;
        }
        return !d.hasOutbound && d.inboundCount <= 1;
    }

    public void onFlowClick(@NonNull StockFlowItemVO f) {
        openRecord.setValue(f);
    }

    // ===================== 对外 =====================

    public long getLotId() {
        return lotId;
    }

    public LiveData<DrugLotDetailVO> getDetail() {
        return detail;
    }

    public LiveData<Banner> getBanner() {
        return banner;
    }

    public LiveData<Boolean> getShowScrap() {
        return showScrap;
    }

    public LiveData<Boolean> getShowOut() {
        return showOut;
    }

    public LiveData<List<String[]>> getInfoRows() {
        return infoRows;
    }

    public SingleLiveEvent<Long> getOpenScrap() {
        return openScrap;
    }

    public SingleLiveEvent<Long> getOpenOutbound() {
        return openOutbound;
    }

    public SingleLiveEvent<Long> getOpenArchive() {
        return openArchive;
    }

    public SingleLiveEvent<Long> getOpenMessage() {
        return openMessage;
    }

    public SingleLiveEvent<Long> getOpenInboundEdit() {
        return openInboundEdit;
    }

    public SingleLiveEvent<Long> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public SingleLiveEvent<Long> getOpenChangeLog() {
        return openChangeLog;
    }

    public SingleLiveEvent<String> getRecordGone() {
        return recordGone;
    }

    public SingleLiveEvent<StockFlowItemVO> getOpenRecord() {
        return openRecord;
    }
}
