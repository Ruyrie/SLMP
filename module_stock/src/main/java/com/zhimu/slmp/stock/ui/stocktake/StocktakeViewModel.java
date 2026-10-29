package com.zhimu.slmp.stock.ui.stocktake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.FeedLossRequest;
import com.zhimu.slmp.stock.model.request.StocktakeConfirmRequest;
import com.zhimu.slmp.stock.model.vo.StocktakeSheetVO;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockValidators;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * M04 盘点与报损（技术准备文档 2.4.5，含 FeedLossSheet）：
 * <ul>
 *     <li>API-M14 盘点表；录入实盘后算出盘盈盘亏与按均价折合金额（页面 300 ms 防抖或失焦后调用 onActualChanged）</li>
 *     <li>确认盘点：只提交已录入的饲料，确认对话框后 API-M15（幂等键）；40902 账面已变化 → bookChanged，“刷新”用 data.items 更新账面与版本，实盘保留</li>
 *     <li>登记报损：API-M18（幂等键），成功后重取 API-M14 并保留实盘</li>
 * </ul>
 * 草稿 page_code M04、biz_key new：保存已录入的实盘数量。
 */
public class StocktakeViewModel extends StockFormViewModel {

    public static final String PAGE_CODE = "M04";
    public static final String F_LOSS_QTY = "input_qty";
    public static final String F_LOSS_REASON = "reason";
    public static final String F_LOSS_REMARK = "remark";
    static final String MSG_EMPTY = "至少录入一种饲料的实盘数量";
    static final String MSG_DONE = "已确认盘点，结存已更新";
    static final String MSG_BOOK_CHANGED = "账面结存已变化，请刷新后重新核对";

    private final FarmContext farmContext;

    private final MutableLiveData<StocktakeSheetVO> sheet = new MutableLiveData<>();
    private final MutableLiveData<String> infoText = new MutableLiveData<>("");
    private final MutableLiveData<Map<Long, String>> actualQty = new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<Map<Long, Diff>> diffs = new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<Map<Long, String>> actualErrors = new MutableLiveData<>(new LinkedHashMap<>());

    private final MutableLiveData<Long> lossFeedId = new MutableLiveData<>();
    private final MutableLiveData<String> lossQty = new MutableLiveData<>("");
    private final MutableLiveData<String> lossReason = new MutableLiveData<>();
    private final MutableLiveData<String> lossRemark = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, String>> lossErrors = new MutableLiveData<>(Collections.emptyMap());

    private final SingleLiveEvent<String> showConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> stocktakeDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> bookChanged = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> showLoss = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> lossDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openHistory = new SingleLiveEvent<>();

    @Nullable
    private List<StocktakeSheetVO.Item> changedItems;

    /** 盘点结果条 */
    public static final class Diff {
        @NonNull
        public final String result;
        @NonNull
        public final String qtyText;
        @Nullable
        public final String amountText;
        public final BigDecimal diffQty;

        Diff(@NonNull String result, @NonNull String qtyText, @Nullable String amountText, BigDecimal diffQty) {
            this.result = result;
            this.qtyText = qtyText;
            this.amountText = amountText;
            this.diffQty = diffQty;
        }
    }

    @Inject
    public StocktakeViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                              @NonNull FarmContext farmContext) {
        super(repository, drafts);
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        load();
    }

    public void load() {
        load(repository.getStocktakeSheet(), s -> {
            applySheet(s);
            markClean();
            checkDraft();
        });
    }

    @Override
    public void retry() {
        load();
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data instanceof StocktakeSheetVO ? ((StocktakeSheetVO) data).items == null
                || ((StocktakeSheetVO) data).items.isEmpty() : super.isEmptyData(data);
    }

    private void applySheet(@NonNull StocktakeSheetVO s) {
        sheet.setValue(s);
        String who = s.operator == null ? null : s.operator.getName();
        infoText.setValue("盘点日期 " + DateFormatter.formatDate(s.stocktakeDate != null ? s.stocktakeDate
                : DateFormatter.todayString()) + (who == null ? "" : StockTexts.DOT + who));
        recomputeAll();
    }

    // ===================== 实盘与差异 =====================

    /** 实盘框变化（300 ms 防抖或失焦后） */
    public void onActualChanged(long feedId, @Nullable String text) {
        Map<Long, String> m = new LinkedHashMap<>(actualQty.getValue() == null ? Collections.emptyMap()
                : actualQty.getValue());
        if (text == null || text.trim().isEmpty()) {
            m.remove(feedId);
        } else {
            m.put(feedId, text.trim());
        }
        actualQty.setValue(m);
        recomputeAll();
    }

    private void recomputeAll() {
        StocktakeSheetVO s = sheet.getValue();
        Map<Long, String> actual = actualQty.getValue() == null ? Collections.emptyMap() : actualQty.getValue();
        Map<Long, Diff> d = new LinkedHashMap<>();
        Map<Long, String> err = new LinkedHashMap<>();
        if (s != null && s.items != null) {
            for (StocktakeSheetVO.Item i : s.items) {
                String text = actual.get(i.feedId);
                String e = checkActual(text);
                if (e != null) {
                    err.put(i.feedId, e);
                    continue;
                }
                Diff diff = diff(i, text);
                if (diff != null) {
                    d.put(i.feedId, diff);
                }
            }
        }
        diffs.setValue(d);
        actualErrors.setValue(err);
    }

    /** 实盘数量校验：不能小于 0、最多两位小数；空为未录入 */
    @Nullable
    static String checkActual(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        BigDecimal v = StockCalc.parse(text);
        if (v == null || v.signum() < 0) {
            return "实盘数量不能小于 0";
        }
        return StockCalc.scaleOf(v) > 2 ? StockValidators.MSG_SCALE : null;
    }

    /** 实盘 − 账面：盘亏“盘亏 12 千克 · 按均价折合 38.40 元”，盘盈同理，相等“账实相符”；未录入返回 null */
    @Nullable
    public static Diff diff(@NonNull StocktakeSheetVO.Item item, @Nullable String actualText) {
        BigDecimal actual = StockCalc.parse(actualText);
        if (actual == null || item.bookQty == null) {
            return null;
        }
        String unit = item.unitText == null ? "千克" : item.unitText;
        BigDecimal d = actual.subtract(item.bookQty);
        if (d.signum() == 0) {
            return new Diff(StockEnums.RESULT_MATCH, "账实相符", null, d);
        }
        BigDecimal amount = item.avgPrice == null ? null
                : d.abs().multiply(item.avgPrice).setScale(2, RoundingMode.HALF_UP);
        String result = d.signum() > 0 ? StockEnums.RESULT_GAIN : StockEnums.RESULT_LOSS;
        String qty = (d.signum() > 0 ? "盘盈 " : "盘亏 ") + StockQtyFormatter.qty(d.abs(), unit);
        return new Diff(result, qty, amount == null ? null : "按均价折合 " + StockCalc.money(amount) + " 元", d);
    }

    // ===================== 确认盘点 =====================

    @VisibleForTesting
    @Nullable
    StocktakeConfirmRequest buildConfirmRequest() {
        StocktakeSheetVO s = sheet.getValue();
        Map<Long, String> actual = actualQty.getValue();
        if (s == null || s.items == null || actual == null) {
            return null;
        }
        StocktakeConfirmRequest req = new StocktakeConfirmRequest();
        req.items = new ArrayList<>();
        for (StocktakeSheetVO.Item i : s.items) {
            BigDecimal v = StockCalc.parse(actual.get(i.feedId));
            if (v == null) {
                continue;
            }
            StocktakeConfirmRequest.Item it = new StocktakeConfirmRequest.Item();
            it.feedId = i.feedId;
            it.bookQty = i.bookQty;
            it.stockVersion = i.stockVersion;
            it.actualQty = v;
            req.items.add(it);
        }
        return req;
    }

    /** 底部“确认盘点”：校验后弹确认对话框 */
    public void confirm() {
        recomputeAll();
        Map<Long, String> err = actualErrors.getValue();
        if (err != null && !err.isEmpty()) {
            return;
        }
        StocktakeConfirmRequest req = buildConfirmRequest();
        if (req == null || req.items.isEmpty()) {
            showMessage(MSG_EMPTY);
            return;
        }
        showConfirm.setValue(confirmText());
    }

    /** “将按实盘数量更新 2 种饲料的结存，生成盘亏 1 条、盘盈 1 条记录。确认后如需更改，要提交修改申请。” */
    @NonNull
    String confirmText() {
        StocktakeConfirmRequest req = buildConfirmRequest();
        int n = req == null ? 0 : req.items.size();
        int gain = 0;
        int loss = 0;
        Map<Long, Diff> d = diffs.getValue();
        if (d != null) {
            for (Diff x : d.values()) {
                if (StockEnums.RESULT_GAIN.equals(x.result)) {
                    gain++;
                } else if (StockEnums.RESULT_LOSS.equals(x.result)) {
                    loss++;
                }
            }
        }
        return "将按实盘数量更新 " + n + " 种饲料的结存，生成盘亏 " + loss + " 条、盘盈 " + gain
                + " 条记录。确认后如需更改，要提交修改申请。";
    }

    /** 对话框“确认” */
    public void doConfirm() {
        StocktakeConfirmRequest req = buildConfirmRequest();
        if (req == null || req.items.isEmpty()) {
            return;
        }
        submitGuarded(req, key -> repository.confirmStocktake(req, key),
                st -> repository.getStocktake(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    onSubmitted(null, StockEnums.BIZ_STOCKTAKE, vo.stocktakeId, farmContext.farmIdOrZero());
                    stocktakeDone.setValue(MSG_DONE);
                }, this::onConfirmError);
    }

    private void onConfirmError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            changedItems = parseItems(e.getData());
            bookChanged.setValue(MSG_BOOK_CHANGED);
            return;
        }
        handleError(e);
    }

    /** 40902 的 data.items（各饲料最新 book_qty 与 stock_version） */
    @Nullable
    static List<StocktakeSheetVO.Item> parseItems(@Nullable JsonElement data) {
        if (data == null || !data.isJsonObject()) {
            return null;
        }
        JsonObject o = data.getAsJsonObject();
        if (!o.has("items")) {
            return null;
        }
        return GsonFactory.get().fromJson(o.get("items"), new TypeToken<List<StocktakeSheetVO.Item>>() {
        }.getType());
    }

    /** bookChanged 对话框“刷新”：用 data.items 覆盖账面与版本（没有时重取盘点表），实盘保留 */
    public void onBookRefresh() {
        List<StocktakeSheetVO.Item> items = changedItems;
        changedItems = null;
        StocktakeSheetVO s = sheet.getValue();
        if (items == null || s == null || s.items == null) {
            launch(repository.getStocktakeSheet(), this::applySheet);
            return;
        }
        for (StocktakeSheetVO.Item fresh : items) {
            for (StocktakeSheetVO.Item old : s.items) {
                if (old.feedId == fresh.feedId) {
                    old.bookQty = fresh.bookQty;
                    old.stockVersion = fresh.stockVersion;
                }
            }
        }
        applySheet(s);
    }

    // ===================== 登记报损 =====================

    /** 卡片“登记报损” */
    public void openLoss(long feedId) {
        lossFeedId.setValue(feedId);
        lossQty.setValue("");
        lossReason.setValue(null);
        lossRemark.setValue("");
        lossErrors.setValue(Collections.emptyMap());
        showLoss.setValue(feedId);
    }

    @Nullable
    public StocktakeSheetVO.Item lossItem() {
        Long id = lossFeedId.getValue();
        StocktakeSheetVO s = sheet.getValue();
        if (id == null || s == null || s.items == null) {
            return null;
        }
        for (StocktakeSheetVO.Item i : s.items) {
            if (i.feedId == id) {
                return i;
            }
        }
        return null;
    }

    @NonNull
    public String lossTitle() {
        StocktakeSheetVO.Item i = lossItem();
        return "登记报损" + StockTexts.DOT + (i == null ? "" : StockTexts.nz(i.feedName));
    }

    @NonNull
    public String lossSubtitle() {
        StocktakeSheetVO.Item i = lossItem();
        return "当前结存 " + (i == null ? "" : StockQtyFormatter.qty(i.bookQty, unitOf(i))) + StockTexts.DOT + "按今天登记";
    }

    @NonNull
    public String lossUnit() {
        return unitOf(lossItem());
    }

    @NonNull
    private static String unitOf(@Nullable StocktakeSheetVO.Item i) {
        return i == null || i.unitText == null ? "千克" : i.unitText;
    }

    /** 报损数量实时与结存比较 */
    public void onLossQtyChanged(@Nullable String text) {
        lossQty.setValue(text == null ? "" : text);
        Map<String, String> m = new LinkedHashMap<>(lossErrors.getValue() == null ? Collections.emptyMap()
                : lossErrors.getValue());
        String over = overStock(text);
        if (over != null) {
            m.put(F_LOSS_QTY, over);
        } else {
            m.remove(F_LOSS_QTY);
        }
        lossErrors.setValue(m);
    }

    public void onLossReasonPicked(@Nullable String reason) {
        lossReason.setValue(reason);
        Map<String, String> m = new LinkedHashMap<>(lossErrors.getValue() == null ? Collections.emptyMap()
                : lossErrors.getValue());
        m.remove(F_LOSS_REASON);
        m.remove(F_LOSS_REMARK);
        lossErrors.setValue(m);
    }

    @Nullable
    private String overStock(@Nullable String text) {
        StocktakeSheetVO.Item i = lossItem();
        BigDecimal v = StockCalc.parse(text);
        if (i == null || v == null || i.bookQty == null || v.compareTo(i.bookQty) <= 0) {
            return null;
        }
        return "超过当前结存 " + StockQtyFormatter.qty(i.bookQty, unitOf(i)) + "，出库后库存不能为负数";
    }

    @VisibleForTesting
    @NonNull
    Map<String, String> validateLoss() {
        Map<String, String> e = new LinkedHashMap<>();
        String q = StockValidators.positive(lossQty.getValue(), StockValidators.MAX_QTY);
        if (q != null) {
            e.put(F_LOSS_QTY, q);
        } else {
            String over = overStock(lossQty.getValue());
            if (over != null) {
                e.put(F_LOSS_QTY, over);
            }
        }
        if (lossReason.getValue() == null) {
            e.put(F_LOSS_REASON, "请选择报损原因");
        }
        String remark = trim(lossRemark.getValue());
        if (StockEnums.LOSS_OTHER.equals(lossReason.getValue()) && remark.isEmpty()) {
            e.put(F_LOSS_REMARK, "请写明报损原因");
        } else if (remark.length() > 100) {
            e.put(F_LOSS_REMARK, "最多 100 个字");
        }
        return e;
    }

    /** 面板“登记报损” */
    public void submitLoss() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> e = validateLoss();
        lossErrors.setValue(e);
        StocktakeSheetVO.Item item = lossItem();
        if (!e.isEmpty() || item == null) {
            return;
        }
        FeedLossRequest req = new FeedLossRequest();
        req.feedId = item.feedId;
        req.inputQty = StockCalc.parse(lossQty.getValue());
        req.inputUnit = StockEnums.INPUT_MEASURE;
        req.reason = lossReason.getValue();
        req.remark = emptyToNull(lossRemark.getValue());
        req.stockVersion = item.stockVersion;
        String unit = unitOf(item);
        String name = StockTexts.nz(item.feedName);
        submitGuarded(req, key -> repository.createFeedLoss(req, key),
                st -> repository.getLoss(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    ITodo.notify(farmContext, StockEnums.BIZ_LOSS, vo.lossId);
                    lossDone.setValue("已登记报损 " + StockQtyFormatter.qty(req.inputQty, unit) + name);
                    launch(repository.getStocktakeSheet(), this::applySheet);
                }, this::onLossError);
    }

    private void onLossError(@NonNull ApiException e) {
        Map<String, String> m = new LinkedHashMap<>();
        switch (e.getCode()) {
            case ErrorCodes.STOCK_NOT_ENOUGH:
                m.put(F_LOSS_QTY, e.getMessage());
                break;
            case ErrorCodes.VERSION_CONFLICT: {
                String cur = e.dataString("current_qty");
                m.put(F_LOSS_QTY, cur == null ? e.getMessage()
                        : "库存已变化，当前结存 " + StockQtyFormatter.qty(StockCalc.parse(cur), lossUnit())
                        + "，已刷新，请核对后重新提交");
                launch(repository.getStocktakeSheet(), this::applySheet);
                break;
            }
            default:
                if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                    m.putAll(e.getFieldErrors());
                } else {
                    showMessage(e.getMessage());
                }
        }
        lossErrors.setValue(m);
    }

    public void onHistory() {
        openHistory.call();
    }

    // ===================== 草稿 =====================

    @Nullable
    @Override
    protected String pageCode() {
        return PAGE_CODE;
    }

    @Nullable
    @Override
    protected String draftBizKey() {
        return "new";
    }

    @NonNull
    @Override
    protected Map<String, String> draftFields() {
        Map<String, String> m = new LinkedHashMap<>();
        Map<Long, String> a = actualQty.getValue();
        if (a != null) {
            for (Map.Entry<Long, String> e : a.entrySet()) {
                m.put("actual:" + e.getKey(), e.getValue());
            }
        }
        return m;
    }

    @Override
    protected void applyDraft(@NonNull Map<String, String> fields) {
        Map<Long, String> a = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey().startsWith("actual:") && e.getValue() != null) {
                try {
                    a.put(Long.parseLong(e.getKey().substring(7)), e.getValue());
                } catch (NumberFormatException ignored) {
                    // 忽略损坏的键
                }
            }
        }
        actualQty.setValue(a);
        recomputeAll();
        restoredActual.setValue(a);
    }

    private final SingleLiveEvent<Map<Long, String>> restoredActual = new SingleLiveEvent<>();

    // ===================== 对外 =====================

    public LiveData<StocktakeSheetVO> getSheet() {
        return sheet;
    }

    public LiveData<String> getInfoText() {
        return infoText;
    }

    public LiveData<Map<Long, String>> getActualQty() {
        return actualQty;
    }

    public LiveData<Map<Long, Diff>> getDiffs() {
        return diffs;
    }

    public LiveData<Map<Long, String>> getActualErrors() {
        return actualErrors;
    }

    public LiveData<Long> getLossFeedId() {
        return lossFeedId;
    }

    public MutableLiveData<String> getLossQty() {
        return lossQty;
    }

    public LiveData<String> getLossReason() {
        return lossReason;
    }

    public MutableLiveData<String> getLossRemark() {
        return lossRemark;
    }

    public LiveData<Map<String, String>> getLossErrors() {
        return lossErrors;
    }

    public SingleLiveEvent<String> getShowConfirm() {
        return showConfirm;
    }

    public SingleLiveEvent<String> getStocktakeDone() {
        return stocktakeDone;
    }

    public SingleLiveEvent<String> getBookChanged() {
        return bookChanged;
    }

    public SingleLiveEvent<Long> getShowLoss() {
        return showLoss;
    }

    public SingleLiveEvent<String> getLossDone() {
        return lossDone;
    }

    public SingleLiveEvent<Void> getOpenHistory() {
        return openHistory;
    }

    /** 恢复草稿后回填实盘框 */
    public SingleLiveEvent<Map<Long, String>> getRestoredActual() {
        return restoredActual;
    }

    /** 报损成功通知首页刷新待办（不结束页面） */
    private static final class ITodo {
        static void notify(@NonNull FarmContext farm, @NonNull String bizType, long bizId) {
            com.zhimu.slmp.common.service.ITodoService todo =
                    com.zhimu.slmp.common.service.ServiceHub.get(com.zhimu.slmp.common.service.ITodoService.class);
            if (todo != null) {
                todo.notifyTodoChanged(new com.zhimu.slmp.common.service.TodoChange(null, bizType, bizId,
                        farm.farmIdOrZero()));
            }
        }
    }
}
