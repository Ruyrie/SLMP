package com.zhimu.slmp.stock.ui.drugscrap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.DrugScrapRequest;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockValidators;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * M07 登记报废（技术准备文档 2.4.9）：API-M26 取批号；数量默认全部结存（疫苗按头份，其他按包装），
 * 原因按状态预选（已停用 PLATFORM_RECALL、已过期 EXPIRED），停用批号说明带入停用原因；报废损失 = 记账数量 × 均价。
 * 校验通过弹确认对话框，“报废”时才生成幂等键提交 API-M31。原因取字典 SCRAP_REASON（待定事项 2），PLATFORM_RECALL 仅停用批号可选。
 */
public class DrugScrapViewModel extends StockFormViewModel {

    public static final String F_QTY = "input_qty";
    public static final String F_REASON = "reason";
    public static final String F_REMARK = "remark";
    public static final String F_DATE = "scrap_date";

    private final BaseDataRepository baseData;
    private final FarmContext farmContext;

    private final MutableLiveData<DrugLotDetailVO> lot = new MutableLiveData<>();
    private final MutableLiveData<String> inputQty = new MutableLiveData<>("");
    private final MutableLiveData<String> reason = new MutableLiveData<>();
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> scrapDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<List<DictItemVO>> reasons = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> unitText = new MutableLiveData<>("");
    private final MutableLiveData<String> maxText = new MutableLiveData<>("");
    private final MediatorLiveData<String> lossText = new MediatorLiveData<>();
    private final MutableLiveData<String> cardSubtitle = new MutableLiveData<>("");

    private final SingleLiveEvent<String[]> showConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrapped = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> closeConfirm = new SingleLiveEvent<>();

    private long lotId;

    @Inject
    public DrugScrapViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                              @NonNull BaseDataRepository baseData, @NonNull FarmContext farmContext) {
        super(repository, drafts);
        this.baseData = baseData;
        this.farmContext = farmContext;
        lossText.setValue("—");
        lossText.addSource(inputQty, v -> {
            clearFieldError(F_QTY);
            lossText.setValue(StockCalc.money(lossAmount()));
        });
        lossText.addSource(remark, v -> clearFieldError(F_REMARK));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        Long id = StockExtras.readId(handle.get(RouterPath.EXTRA_LOT_ID));
        load(id == null ? 0 : id);
    }

    public void load(long id) {
        lotId = id;
        Single<List<DictItemVO>> dict = baseData.getDicts(DictTypes.SCRAP_REASON, null)
                .onErrorReturnItem(Collections.emptyList());
        load(Single.zip(repository.getDrugLot(id), dict, Init::new), this::apply);
    }

    @Override
    public void retry() {
        load(lotId);
    }

    private void apply(@NonNull Init init) {
        DrugLotDetailVO d = init.lot;
        lot.setValue(d);
        boolean recalled = StockEnums.LOT_RECALLED.equals(d.lotStatus);
        List<DictItemVO> list = new ArrayList<>();
        for (DictItemVO item : init.reasons.isEmpty() ? defaultReasons() : init.reasons) {
            if (StockEnums.SCRAP_PLATFORM_RECALL.equals(item.getCode()) && !recalled) {
                continue;
            }
            list.add(item);
        }
        if (recalled) {
            reason.setValue(StockEnums.SCRAP_PLATFORM_RECALL);
            remark.setValue(StockTexts.nz(d.recallReason));
        } else if (StockEnums.LOT_EXPIRED.equals(d.lotStatus)) {
            reason.setValue(StockEnums.SCRAP_EXPIRED);
        }
        reasons.setValue(list);
        unitText.setValue(isDose(d) ? StockQtyFormatter.unitText(d.stock) : packUnit(d));
        BigDecimal max = maxQty(d);
        inputQty.setValue(StockCalc.plain(max));
        maxText.setValue("最多 " + StockQtyFormatter.qty(max, unitText.getValue()) + "，全部报废后该批号结存清零");
        cardSubtitle.setValue(cardSubtitleOf(d));
        lossText.setValue(StockCalc.money(lossAmount()));
        markClean();
    }

    /** 字典取不到时的默认原因（服务端 2.4 节：PLATFORM_RECALL、EXPIRED 为系统项，DAMAGED、OTHER） */
    @NonNull
    static List<DictItemVO> defaultReasons() {
        String json = "[{\"code\":\"PLATFORM_RECALL\",\"name\":\"平台停用\"},{\"code\":\"EXPIRED\",\"name\":\"已过期\"},"
                + "{\"code\":\"DAMAGED\",\"name\":\"破损\"},{\"code\":\"OTHER\",\"name\":\"其他\",\"remark_required\":true}]";
        return com.zhimu.slmp.network.GsonFactory.get().fromJson(json,
                new com.google.gson.reflect.TypeToken<List<DictItemVO>>() {
                }.getType());
    }

    static boolean isDose(@Nullable DrugLotDetailVO d) {
        return d != null && d.stock != null && StockEnums.UNIT_DOSE.equals(d.stock.unit);
    }

    @NonNull
    static String packUnit(@NonNull DrugLotDetailVO d) {
        return StockQtyFormatter.hasPack(d.stock) ? d.stock.packUnit : StockQtyFormatter.unitText(d.stock);
    }

    /** 报废上限：疫苗为头份，其他为包装数 */
    @NonNull
    static BigDecimal maxQty(@NonNull DrugLotDetailVO d) {
        if (d.stock == null || d.stock.value == null) {
            return BigDecimal.ZERO;
        }
        if (isDose(d) || !StockQtyFormatter.hasPack(d.stock)) {
            return d.stock.value;
        }
        return StockCalc.toPack(d.stock.value, d.stock.packSize);
    }

    /** “批号 2025047 · 结存 300 头份\n（30 瓶）”；兽药“批号 2024118 · 结存 3 袋” */
    @NonNull
    static String cardSubtitleOf(@NonNull DrugLotDetailVO d) {
        String head = "批号 " + StockTexts.nz(d.lotNo) + StockTexts.DOT + "结存 ";
        if (isDose(d)) {
            String s = head + StockQtyFormatter.qty(d.stock.value, StockQtyFormatter.unitText(d.stock));
            return StockQtyFormatter.hasPack(d.stock) ? s + "\n（" + StockQtyFormatter.packText(d.stock) + "）" : s;
        }
        return head + StockQtyFormatter.packText(d.stock);
    }

    /** 报废损失 = 记账数量 × 均价（没有均价时按包装价） */
    @Nullable
    BigDecimal lossAmount() {
        DrugLotDetailVO d = lot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        if (d == null || q == null) {
            return null;
        }
        if (isDose(d) || !StockQtyFormatter.hasPack(d.stock)) {
            return StockCalc.amount(q, d.avgPrice);
        }
        if (d.avgPrice != null) {
            return StockCalc.amount(StockCalc.toMeasure(q, d.stock.packSize), d.avgPrice);
        }
        return StockCalc.amount(q, d.packPrice);
    }

    // ===================== 选择 =====================

    public void onReasonPicked(@Nullable String code) {
        reason.setValue(code);
        clearFieldError(F_REASON);
        clearFieldError(F_REMARK);
    }

    public void onDatePicked(@Nullable String date) {
        scrapDate.setValue(date);
        String e = validateDate();
        if (e != null) {
            putFieldError(F_DATE, e);
        } else {
            clearFieldError(F_DATE);
        }
    }

    /** 当前原因是否要求填写说明（OTHER 或字典 remark_required） */
    public boolean remarkRequired() {
        String r = reason.getValue();
        if (StockEnums.SCRAP_OTHER.equals(r)) {
            return true;
        }
        List<DictItemVO> list = reasons.getValue();
        if (list != null && r != null) {
            for (DictItemVO i : list) {
                if (r.equals(i.getCode())) {
                    return i.isRemarkRequired();
                }
            }
        }
        return false;
    }

    // ===================== 校验与提交 =====================

    @Nullable
    private String validateDate() {
        String date = scrapDate.getValue();
        String e = StockValidators.notAfterToday(date, "报废日期");
        if (e != null) {
            return e;
        }
        DrugLotDetailVO d = lot.getValue();
        String first = d == null || d.firstInbound == null ? null : d.firstInbound.inboundDate;
        if (first != null && StockValidators.isBefore(date, first)) {
            LocalDate fd = DateFormatter.parseDate(first);
            return "报废日期不能早于入库日期 " + (fd == null ? first : DateFormatter.monthDay(fd));
        }
        return null;
    }

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        DrugLotDetailVO d = lot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        if (q == null || q.signum() <= 0) {
            e.put(F_QTY, StockValidators.MSG_GT_ZERO);
        } else if (d != null && q.compareTo(maxQty(d)) > 0) {
            e.put(F_QTY, "超过当前结存 " + StockQtyFormatter.qty(maxQty(d), unitText.getValue()));
        } else if (isDose(d) && !StockCalc.isInteger(q)) {
            e.put(F_QTY, "头份必须是整数");
        } else if (StockCalc.scaleOf(q) > 2) {
            e.put(F_QTY, StockValidators.MSG_SCALE);
        }
        if (reason.getValue() == null) {
            e.put(F_REASON, "请选择报废原因");
        }
        String r = trim(remark.getValue());
        if (remarkRequired() && r.isEmpty()) {
            e.put(F_REMARK, "请写明报废原因");
        } else if (r.length() > 100) {
            e.put(F_REMARK, "最多 100 个字");
        }
        String de = validateDate();
        if (de != null) {
            e.put(F_DATE, de);
        }
        return e;
    }

    /** 底部“登记报废”：校验通过后弹确认对话框 */
    public void submit() {
        Map<String, String> e = validate();
        setErrors(e);
        DrugLotDetailVO d = lot.getValue();
        if (!e.isEmpty() || d == null) {
            return;
        }
        showConfirm.setValue(new String[]{confirmTitle(d), confirmMessage()});
    }

    @NonNull
    static String confirmTitle(@NonNull DrugLotDetailVO d) {
        return "报废这批" + StockEnums.categoryText(d.category) + "？";
    }

    /** 全部报废 / 部分报废的对话框正文 */
    @NonNull
    String confirmMessage() {
        DrugLotDetailVO d = lot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        if (d == null || q == null) {
            return "";
        }
        String name = StockTexts.nz(d.drugName) + " " + StockTexts.nz(d.lotNo);
        String loss = NumberFormatter.formatAmountSummary(lossAmount());
        String tail = "，损失 " + loss + "，不计入批次成本。确认后如需修改，要提交修改申请。";
        String qty = StockQtyFormatter.qty(q, unitText.getValue());
        if (isAll(d, q)) {
            String pack = isDose(d) && StockQtyFormatter.hasPack(d.stock) ? "（" + StockQtyFormatter.packText(d.stock) + "）"
                    : "";
            return name + " 共 " + qty + pack + "将全部报废" + tail;
        }
        return name + " 中的 " + qty + "将报废" + tail;
    }

    static boolean isAll(@NonNull DrugLotDetailVO d, @NonNull BigDecimal q) {
        return q.compareTo(maxQty(d)) >= 0;
    }

    /** 对话框“报废” */
    public void doScrap() {
        DrugLotDetailVO d = lot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        if (d == null || q == null) {
            return;
        }
        DrugScrapRequest req = new DrugScrapRequest();
        req.drugLotId = d.lotId;
        req.inputQty = q;
        req.inputUnit = isDose(d) || !StockQtyFormatter.hasPack(d.stock) ? StockEnums.INPUT_MEASURE
                : StockEnums.INPUT_PACK;
        req.reason = reason.getValue();
        req.remark = emptyToNull(remark.getValue());
        req.scrapDate = scrapDate.getValue();
        req.stockVersion = d.stockVersion;
        boolean all = isAll(d, q);
        String qty = StockQtyFormatter.qty(q, unitText.getValue());
        submitGuarded(req, key -> repository.createDrugScrap(req, key),
                st -> repository.getLoss(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    onSubmitted(null, StockEnums.BIZ_LOSS, vo.lossId, farmContext.farmIdOrZero());
                    scrapped.setValue(all ? "已登记报废，" + StockTexts.nz(d.drugName) + " " + StockTexts.nz(d.lotNo)
                            + " 结存已清零" : "已登记报废 " + qty);
                }, this::onScrapError);
    }

    private void onScrapError(@NonNull ApiException e) {
        closeConfirm.call();
        switch (e.getCode()) {
            case ErrorCodes.STOCK_NOT_ENOUGH:
                putFieldError(F_QTY, e.getMessage());
                return;
            case ErrorCodes.VERSION_CONFLICT: {
                String cur = e.dataString("current_qty");
                putFieldError(F_QTY, cur == null ? e.getMessage() : "库存已变化，当前结存 "
                        + StockQtyFormatter.qty(StockCalc.parse(cur), unitText.getValue()) + "，请刷新后重试");
                launch(repository.getDrugLot(lotId), lot::setValue);
                return;
            }
            default:
                if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                    setErrors(e.getFieldErrors());
                    return;
                }
                handleError(e);
        }
    }

    @NonNull
    @Override
    protected Map<String, String> draftFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(F_QTY, inputQty.getValue());
        m.put(F_REASON, reason.getValue());
        m.put(F_REMARK, remark.getValue());
        m.put(F_DATE, scrapDate.getValue());
        return m;
    }

    // ===================== 对外 =====================

    public LiveData<DrugLotDetailVO> getLot() {
        return lot;
    }

    public MutableLiveData<String> getInputQty() {
        return inputQty;
    }

    public LiveData<String> getReason() {
        return reason;
    }

    public MutableLiveData<String> getRemark() {
        return remark;
    }

    public LiveData<String> getScrapDate() {
        return scrapDate;
    }

    public LiveData<List<DictItemVO>> getReasons() {
        return reasons;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<String> getMaxText() {
        return maxText;
    }

    public LiveData<String> getLossText() {
        return lossText;
    }

    public LiveData<String> getCardSubtitle() {
        return cardSubtitle;
    }

    public SingleLiveEvent<String[]> getShowConfirm() {
        return showConfirm;
    }

    public SingleLiveEvent<String> getScrapped() {
        return scrapped;
    }

    public SingleLiveEvent<Void> getCloseConfirm() {
        return closeConfirm;
    }

    static final class Init {
        final DrugLotDetailVO lot;
        final List<DictItemVO> reasons;

        Init(DrugLotDetailVO lot, List<DictItemVO> reasons) {
            this.lot = lot;
            this.reasons = reasons;
        }
    }
}
