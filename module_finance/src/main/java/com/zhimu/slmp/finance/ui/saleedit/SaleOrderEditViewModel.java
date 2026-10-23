package com.zhimu.slmp.finance.ui.saleedit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.PenQuantity;
import com.zhimu.slmp.finance.model.request.SaleOrderRequest;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderCreatedVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.FinanceIdemKeeper;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.finance.util.SaleAmountCalculator;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * E06 登记出栏单（新增 / 编辑未收款出栏单）：批次与休药期拦截、多栏舍分配、均重与销售金额本地预估（技术准备文档 2.6.2）。
 * 休药期内 canSubmit 恒假；42202 改写休药状态，42205 改写最多数量；新增成功以 E07 替换当前页。
 */
public class SaleOrderEditViewModel extends FinanceViewModel {

    public static final String F_BATCH = "batch_id";
    public static final String F_SALE_DATE = "sale_date";
    public static final String F_QUANTITY = "quantity";
    public static final String F_PENS = "pen_quantities";
    public static final String F_WEIGHT = "total_weight";
    public static final String F_PRICE = "unit_price";
    public static final String F_BUYER = "buyer_name";
    public static final String F_DUE = "due_date";
    static final BigDecimal PRICE_MAX = new BigDecimal("99999.99");
    static final int BUYER_MAX = 30;

    /** 多栏舍时的一行（栏舍、该批次在栏数、填写的出栏数） */
    public static final class PenRow {
        public final long penId;
        public final String penName;
        public final int stockCount;
        public final String quantityText;

        PenRow(long penId, String penName, int stockCount, String quantityText) {
            this.penId = penId;
            this.penName = penName;
            this.stockCount = stockCount;
            this.quantityText = quantityText;
        }
    }

    private final SaleOrderRepository repository;
    private final Gson gson = GsonFactory.get();
    private final FinanceIdemKeeper idem = new FinanceIdemKeeper("sale_order");

    /** 双向绑定 */
    public final MutableLiveData<String> saleDate = new MutableLiveData<>(DateFormatter.todayString());
    public final MutableLiveData<String> dueDate = new MutableLiveData<>();
    public final MutableLiveData<String> quantityText = new MutableLiveData<>("");
    public final MutableLiveData<String> totalWeightText = new MutableLiveData<>("");
    public final MutableLiveData<String> unitPriceText = new MutableLiveData<>("");
    public final MutableLiveData<String> buyerName = new MutableLiveData<>("");
    public final MutableLiveData<String> pricingMethod = new MutableLiveData<>(FinanceConst.BY_WEIGHT);

    private final MutableLiveData<List<SaleBatchOptionVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<SaleBatchOptionVO> selectedBatch = new MutableLiveData<>();
    private final MutableLiveData<Boolean> inWithdrawal = new MutableLiveData<>(false);
    private final MutableLiveData<String> sellableDate = new MutableLiveData<>();
    private final MutableLiveData<Integer> withdrawalDaysLeft = new MutableLiveData<>();
    private final MutableLiveData<SalePrefillVO> prefill = new MutableLiveData<>();
    private final MutableLiveData<List<PenRow>> penQuantities = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<BigDecimal> avgWeight = new MutableLiveData<>();
    private final MutableLiveData<BigDecimal> saleAmount = new MutableLiveData<>();
    private final MutableLiveData<Integer> maxQuantity = new MutableLiveData<>(0);
    private final MutableLiveData<SaleOrderDetailVO> editDetail = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> noBatches = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);

    private final SingleLiveEvent<NavTarget> openDetail = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> showBatchSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> confirmDiscard = new SingleLiveEvent<>();

    private long saleOrderId;
    private long presetBatchId;
    private boolean filling;

    @Inject
    public SaleOrderEditViewModel(@NonNull SaleOrderRepository repository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
        canSubmit.addSource(selectedBatch, v -> recompute());
        canSubmit.addSource(inWithdrawal, v -> recompute());
        canSubmit.addSource(submitting, v -> recompute());
        canSubmit.addSource(quantityText, v -> onAmountInputChanged(true));
        canSubmit.addSource(totalWeightText, v -> onAmountInputChanged(false));
        canSubmit.addSource(unitPriceText, v -> onAmountInputChanged(false));
        canSubmit.addSource(pricingMethod, v -> onAmountInputChanged(false));
        canSubmit.setValue(false);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        Long id = handle.get(FinanceExtras.SALE_ORDER_ID);
        saleOrderId = id == null ? 0 : id;
        Long b = handle.get(FinanceExtras.BATCH_ID);
        presetBatchId = b == null ? 0 : b;
        editMode.setValue(saleOrderId > 0);
        init();
    }

    // ===== 对外 LiveData =====

    public LiveData<List<SaleBatchOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public LiveData<SaleBatchOptionVO> getSelectedBatch() {
        return selectedBatch;
    }

    public LiveData<Boolean> getInWithdrawal() {
        return inWithdrawal;
    }

    public LiveData<String> getSellableDate() {
        return sellableDate;
    }

    public LiveData<Integer> getWithdrawalDaysLeft() {
        return withdrawalDaysLeft;
    }

    public LiveData<SalePrefillVO> getPrefill() {
        return prefill;
    }

    public LiveData<List<PenRow>> getPenQuantities() {
        return penQuantities;
    }

    public LiveData<BigDecimal> getAvgWeight() {
        return avgWeight;
    }

    public LiveData<BigDecimal> getSaleAmount() {
        return saleAmount;
    }

    public LiveData<Integer> getMaxQuantity() {
        return maxQuantity;
    }

    public LiveData<SaleOrderDetailVO> getEditDetail() {
        return editDetail;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Boolean> getNoBatches() {
        return noBatches;
    }

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public SingleLiveEvent<NavTarget> getOpenDetail() {
        return openDetail;
    }

    public SingleLiveEvent<String> getFinishWithResult() {
        return finishWithResult;
    }

    public SingleLiveEvent<Boolean> getShowBatchSheet() {
        return showBatchSheet;
    }

    public SingleLiveEvent<Boolean> getConfirmDiscard() {
        return confirmDiscard;
    }

    // ===== 加载 =====

    void init() {
        if (denyStaff()) {
            return;
        }
        if (saleOrderId > 0) {
            load(repository.getOrderDetail(saleOrderId).flatMap(d -> repository.getBatchOptions()
                    .onErrorReturnItem(Collections.emptyList()).map(list -> new Object[]{d, list})), arr -> {
                @SuppressWarnings("unchecked")
                List<SaleBatchOptionVO> list = (List<SaleBatchOptionVO>) arr[1];
                batchOptions.setValue(list);
                fillEdit((SaleOrderDetailVO) arr[0], list);
            });
        } else {
            load(repository.getBatchOptions(), list -> {
                batchOptions.setValue(list);
                noBatches.setValue(list.isEmpty());
                if (presetBatchId > 0) {
                    selectBatch(presetBatchId);
                } else {
                    loadPrefill(null);
                }
            });
        }
    }

    /** 列表为空时不切换整页空状态（批次框下提示“去入栏”） */
    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    @Override
    public void retry() {
        init();
    }

    private void fillEdit(@NonNull SaleOrderDetailVO d, @NonNull List<SaleBatchOptionVO> list) {
        filling = true;
        editDetail.setValue(d);
        SaleBatchOptionVO opt = null;
        for (SaleBatchOptionVO o : list) {
            if (o.getBatchId() == d.getBatchId()) {
                opt = o;
            }
        }
        if (opt == null) {
            opt = batchFromDetail(d);
        }
        applyBatch(opt, d.getQuantity());
        saleDate.setValue(d.getSaleDate());
        dueDate.setValue(d.getDueDate());
        pricingMethod.setValue(d.getPricingMethod());
        quantityText.setValue(String.valueOf(d.getQuantity()));
        totalWeightText.setValue(plain(d.getTotalWeight()));
        unitPriceText.setValue(plain(d.getUnitPrice()));
        buyerName.setValue(d.getBuyerName());
        if (opt.getPens().size() > 1) {
            List<PenRow> rows = new ArrayList<>();
            for (SaleBatchOptionVO.PenStock p : opt.getPens()) {
                int q = 0;
                for (SaleOrderDetailVO.PenAllocation a : d.getPenAllocations()) {
                    if (a.getPenId() == p.getPenId()) {
                        q = a.getQuantity();
                    }
                }
                rows.add(new PenRow(p.getPenId(), p.getPenName(), p.getStockCount() + q, String.valueOf(q)));
            }
            penQuantities.setValue(rows);
        }
        filling = false;
        onAmountInputChanged(false);
        loadPrefill(d.getBatchId());
    }

    /** 已全部出栏的批次不在 E14 中：按出栏单拼一个批次项（存栏 0，各栏舍按原分配） */
    @NonNull
    private SaleBatchOptionVO batchFromDetail(@NonNull SaleOrderDetailVO d) {
        JsonObject o = new JsonObject();
        o.addProperty("batch_id", d.getBatchId());
        o.addProperty("batch_no", d.getBatchNo());
        o.addProperty("pen_name", d.getPenName());
        o.addProperty("category_name", d.getCategoryName());
        o.addProperty("breed_name", d.getBreedName());
        o.addProperty("count_unit_text", d.getCountUnitText());
        o.addProperty("current_count", 0);
        o.addProperty("in_withdrawal", false);
        JsonArray pens = new JsonArray();
        for (SaleOrderDetailVO.PenAllocation a : d.getPenAllocations()) {
            JsonObject p = new JsonObject();
            p.addProperty("pen_id", a.getPenId());
            p.addProperty("pen_name", a.getPenName());
            p.addProperty("stock_count", 0);
            pens.add(p);
        }
        o.add("pens", pens);
        return gson.fromJson(o, SaleBatchOptionVO.class);
    }

    private void loadPrefill(@Nullable Long batchId) {
        launch(repository.getPrefill(batchId, saleDate.getValue()), p -> {
            prefill.setValue(p);
            if (isBlank(buyerName.getValue()) && p.getLastBuyer() != null && saleOrderId == 0) {
                buyerName.setValue(p.getLastBuyer());
            }
        }, e -> {
        });
    }

    // ===== 批次 =====

    public void openBatchSheet() {
        if (saleOrderId > 0) {
            return;
        }
        showBatchSheet.setValue(true);
    }

    /** 选择批次（休药中的批次也可选，选中后拦截提交） */
    public void selectBatch(long batchId) {
        for (SaleBatchOptionVO o : batchOptions.getValue()) {
            if (o.getBatchId() == batchId) {
                applyBatch(o, 0);
                fieldErrors.setValue(Collections.emptyMap());
                idem.clear();
                loadPrefill(batchId);
                return;
            }
        }
    }

    private void applyBatch(@NonNull SaleBatchOptionVO o, int originalQuantity) {
        selectedBatch.setValue(o);
        inWithdrawal.setValue(o.isInWithdrawal());
        sellableDate.setValue(o.getSellableDate());
        withdrawalDaysLeft.setValue(o.getWithdrawalDaysLeft());
        maxQuantity.setValue(o.getCurrentCount() + originalQuantity);
        if (o.getPens().size() > 1) {
            List<PenRow> rows = new ArrayList<>();
            for (SaleBatchOptionVO.PenStock p : o.getPens()) {
                rows.add(new PenRow(p.getPenId(), p.getPenName(), p.getStockCount(), ""));
            }
            penQuantities.setValue(rows);
        } else {
            penQuantities.setValue(Collections.emptyList());
        }
        onAmountInputChanged(false);
    }

    public void changeSaleDate(@NonNull String date) {
        saleDate.setValue(date);
        idem.clear();
        SaleBatchOptionVO b = selectedBatch.getValue();
        loadPrefill(b == null ? null : b.getBatchId());
    }

    public void setDueDate(@Nullable String date) {
        dueDate.setValue(date);
        idem.clear();
    }

    /** 切换计价方式：清空已填单价 */
    public void switchPricing(@NonNull String method) {
        if (method.equals(pricingMethod.getValue())) {
            return;
        }
        pricingMethod.setValue(method);
        unitPriceText.setValue("");
    }

    public void setPenQuantity(int index, @Nullable String text) {
        List<PenRow> rows = new ArrayList<>(penQuantities.getValue());
        if (index < 0 || index >= rows.size()) {
            return;
        }
        PenRow r = rows.get(index);
        rows.set(index, new PenRow(r.penId, r.penName, r.stockCount, text == null ? "" : text));
        penQuantities.setValue(rows);
        idem.clear();
    }

    public void openEntry() {
        open(NavTarget.of(RouterPath.L08));
    }

    // ===== 计算 =====

    private void onAmountInputChanged(boolean quantityChanged) {
        Integer qty = SaleAmountCalculator.parseInt(quantityText.getValue());
        BigDecimal weight = SaleAmountCalculator.parseDecimal(totalWeightText.getValue());
        BigDecimal price = SaleAmountCalculator.parseDecimal(unitPriceText.getValue());
        avgWeight.setValue(SaleAmountCalculator.avgWeight(weight, qty));
        saleAmount.setValue(SaleAmountCalculator.saleAmount(pricingMethod.getValue(), qty, weight, price));
        if (quantityChanged && !filling) {
            // 数量超过存栏时立即在框下报错
            Map<String, String> errors = new LinkedHashMap<>(fieldErrors.getValue());
            Integer max = maxQuantity.getValue();
            if (qty != null && max != null && selectedBatch.getValue() != null && qty > max) {
                errors.put(F_QUANTITY, "不能超过批次当前存栏 " + FinanceFormat.count(max, unitText()));
            } else {
                errors.remove(F_QUANTITY);
            }
            fieldErrors.setValue(errors);
        }
        if (!filling) {
            idem.clear();
        }
        recompute();
    }

    private void recompute() {
        boolean ok = selectedBatch.getValue() != null && !Boolean.TRUE.equals(inWithdrawal.getValue())
                && !Boolean.TRUE.equals(submitting.getValue());
        if (!Objects.equals(canSubmit.getValue(), ok)) {
            canSubmit.setValue(ok);
        }
    }

    // ===== 显示文字 =====

    @NonNull
    public String unitText() {
        SaleBatchOptionVO b = selectedBatch.getValue();
        return b == null || b.getCountUnitText() == null ? "头" : b.getCountUnitText();
    }

    /** 单价单位“元/千克”“元/头” */
    @NonNull
    public String priceUnit() {
        return FinanceConst.BY_HEAD.equals(pricingMethod.getValue()) ? "元/" + unitText() : "元/千克";
    }

    @NonNull
    public String batchHelper() {
        SaleBatchOptionVO b = selectedBatch.getValue();
        if (b == null) {
            return Boolean.TRUE.equals(noBatches.getValue()) ? "当前养殖场没有在养批次，先登记批次入栏。" : "";
        }
        StringBuilder sb = new StringBuilder(b.getPenName() == null ? "" : b.getPenName());
        if (b.isInWithdrawal()) {
            sb.append(" · ").append(b.getBreedName() == null ? "" : b.getBreedName()).append(b.getCategoryName());
        } else {
            sb.append(" · ").append(b.getDayAge()).append(" 日龄");
        }
        sb.append(" · 存栏 ").append(FinanceFormat.count(b.getCurrentCount(), unitText()));
        if (!b.isInWithdrawal()) {
            sb.append(" · 不在休药期");
        }
        return sb.toString();
    }

    /** 批次标签“可出栏”“休药中 · 剩 10 天”；没有选择批次时为 null */
    @Nullable
    public String batchTag() {
        SaleBatchOptionVO b = selectedBatch.getValue();
        if (b == null) {
            return null;
        }
        if (Boolean.TRUE.equals(inWithdrawal.getValue())) {
            Integer left = withdrawalDaysLeft.getValue();
            return left == null ? "休药中" : "休药中 · 剩 " + left + " 天";
        }
        return "可出栏";
    }

    @NonNull
    public String withdrawalNotice() {
        return "批次在休药期内，暂不能登记出栏，可出栏日期 " + FinanceFormat.monthDay(sellableDate.getValue())
                + "。休药期结束前“提交”不可用，届时再登记出栏单。";
    }

    @NonNull
    public String quantityHelper() {
        Integer max = maxQuantity.getValue();
        return selectedBatch.getValue() == null || max == null ? "" : "最多 " + FinanceFormat.count(max, unitText());
    }

    @NonNull
    public String weightHelper() {
        BigDecimal avg = avgWeight.getValue();
        return avg == null ? "自动计算均重" : "均重 " + avg.toPlainString() + FinanceFormat.NBSP + "千克";
    }

    @NonNull
    public String priceHelper() {
        SalePrefillVO p = prefill.getValue();
        SalePrefillVO.MarketPrice m = p == null ? null : p.getMarketPrice();
        if (m == null) {
            return "";
        }
        String name = m.getProvinceName() + m.getCategoryName() + "出栏价";
        if (m.getPrice() == null) {
            return "参考：今天还没有" + name;
        }
        boolean today = DateFormatter.todayString().equals(m.getPriceDate());
        return "参考：" + name + (today ? "今天" : FinanceFormat.monthDay(m.getPriceDate())) + "每千克 "
                + FinanceFormat.price(m.getPrice()) + " 元";
    }

    @NonNull
    public String dueHelper() {
        return isBlank(dueDate.getValue()) ? "不填写时，按出栏后第 7 天提醒收款" : "约定日前 1 天提醒你和场长收款";
    }

    /** 规则横幅：剩余存栏随数量实时变化 */
    @NonNull
    public String ruleNotice() {
        SaleBatchOptionVO b = selectedBatch.getValue();
        Integer max = maxQuantity.getValue();
        if (b == null || max == null) {
            return "";
        }
        String head = "出栏数量不能超过批次存栏 " + FinanceFormat.count(max, unitText()) + "。";
        Integer qty = SaleAmountCalculator.parseInt(quantityText.getValue());
        Integer left = SaleAmountCalculator.remaining(max, qty);
        if (left == null || qty > max) {
            return head + "提交后自动扣减存栏；全部出栏后批次自动结束。";
        }
        if (left == 0) {
            return head + "提交后 " + b.getBatchNo() + " 全部出栏，批次自动结束并结算核算。";
        }
        return head + "提交后自动扣减存栏，" + b.getBatchNo() + " 剩 " + FinanceFormat.count(left, unitText())
                + "；全部出栏后批次自动结束。";
    }

    // ===== 提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        SaleBatchOptionVO b = selectedBatch.getValue();
        if (b == null) {
            e.put(F_BATCH, "请选择批次");
        }
        LocalDate sale = DateFormatter.parseDate(saleDate.getValue());
        if (sale == null) {
            e.put(F_SALE_DATE, "请选择出栏日期");
        } else if (sale.isAfter(DateFormatter.today())) {
            e.put(F_SALE_DATE, "出栏日期不能晚于今天");
        } else if (b != null) {
            LocalDate entry = DateFormatter.parseDate(b.getEntryDate());
            LocalDate sellable = DateFormatter.parseDate(sellableDate.getValue());
            if (entry != null && sale.isBefore(entry)) {
                e.put(F_SALE_DATE, "出栏日期不能早于入栏日期 " + DateFormatter.monthDay(entry));
            } else if (sellable != null && sale.isBefore(sellable)) {
                e.put(F_SALE_DATE, "出栏日期不能早于可出栏日期 " + DateFormatter.monthDay(sellable));
            }
        }
        String qText = quantityText.getValue() == null ? "" : quantityText.getValue().trim();
        Integer qty = SaleAmountCalculator.parseInt(qText);
        Integer max = maxQuantity.getValue();
        if (qText.isEmpty()) {
            e.put(F_QUANTITY, "请输入数量");
        } else if (qty == null || qty <= 0) {
            e.put(F_QUANTITY, "数量必须大于 0");
        } else if (b != null && max != null && qty > max) {
            e.put(F_QUANTITY, "不能超过批次当前存栏 " + FinanceFormat.count(max, unitText()));
        }
        List<PenRow> rows = penQuantities.getValue();
        if (!rows.isEmpty() && qty != null && qty > 0) {
            int sum = 0;
            for (PenRow r : rows) {
                Integer v = SaleAmountCalculator.parseInt(r.quantityText);
                int n = v == null ? 0 : v;
                if (n < 0 || n > r.stockCount) {
                    e.put(F_PENS, r.penName + "本批次在栏 " + FinanceFormat.count(r.stockCount, unitText()) + "，不能超过");
                    break;
                }
                sum += n;
            }
            if (!e.containsKey(F_PENS) && sum != qty) {
                e.put(F_PENS, "各栏舍出栏数合计应等于出栏数量 " + FinanceFormat.count(qty, unitText()));
            }
        }
        decimal(e, F_WEIGHT, totalWeightText.getValue(), "请输入总重", "总重必须大于 0", "总重最多保留 2 位小数", null, null);
        decimal(e, F_PRICE, unitPriceText.getValue(), "请输入单价", "单价必须大于 0", "单价最多保留 2 位小数", PRICE_MAX,
                "单价不能超过 99,999.99 元");
        String buyer = buyerName.getValue() == null ? "" : buyerName.getValue().trim();
        if (buyer.isEmpty()) {
            e.put(F_BUYER, "请填写收购商");
        } else if (buyer.length() > BUYER_MAX) {
            e.put(F_BUYER, "收购商名称不能超过 30 字");
        }
        LocalDate due = DateFormatter.parseDate(dueDate.getValue());
        if (due != null && sale != null && due.isBefore(sale)) {
            e.put(F_DUE, "约定收款日期不能早于出栏日期");
        }
        return e;
    }

    private static void decimal(@NonNull Map<String, String> e, @NonNull String key, @Nullable String text,
                                @NonNull String empty, @NonNull String notPositive, @NonNull String scale,
                                @Nullable BigDecimal max, @Nullable String overMax) {
        String t = text == null ? "" : text.trim();
        BigDecimal v = SaleAmountCalculator.parseDecimal(t);
        if (t.isEmpty()) {
            e.put(key, empty);
        } else if (v == null || v.signum() <= 0) {
            e.put(key, notPositive);
        } else if (v.stripTrailingZeros().scale() > 2) {
            e.put(key, scale);
        } else if (max != null && v.compareTo(max) > 0) {
            e.put(key, overMax);
        }
    }

    @NonNull
    SaleOrderRequest buildRequest() {
        SaleBatchOptionVO b = selectedBatch.getValue();
        boolean edit = saleOrderId > 0;
        List<PenQuantity> pens = null;
        if (!penQuantities.getValue().isEmpty()) {
            pens = new ArrayList<>();
            for (PenRow r : penQuantities.getValue()) {
                Integer v = SaleAmountCalculator.parseInt(r.quantityText);
                pens.add(new PenQuantity(r.penId, v == null ? 0 : v));
            }
        }
        SaleOrderDetailVO d = editDetail.getValue();
        Integer qty = SaleAmountCalculator.parseInt(quantityText.getValue());
        return new SaleOrderRequest(edit || b == null ? null : b.getBatchId(), saleDate.getValue(),
                qty == null ? 0 : qty, pens, SaleAmountCalculator.parseDecimal(totalWeightText.getValue()),
                pricingMethod.getValue(), SaleAmountCalculator.parseDecimal(unitPriceText.getValue()),
                buyerName.getValue() == null ? "" : buyerName.getValue().trim(),
                isBlank(dueDate.getValue()) ? null : dueDate.getValue(), edit && d != null ? d.getVersion() : null);
    }

    public void submit() {
        if (!Boolean.TRUE.equals(canSubmit.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        SaleOrderRequest req = buildRequest();
        submitting.setValue(true);
        if (saleOrderId > 0) {
            launch(repository.updateOrder(saleOrderId, req), d -> {
                submitting.setValue(false);
                notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, saleOrderId);
                finishWithResult.setValue("已保存修改");
            }, e -> onSubmitError(e, null));
        } else {
            String key = idem.keyFor(req);
            launch(repository.createOrder(req, key), this::onCreated, e -> onSubmitError(e, key));
        }
    }

    private void onCreated(@NonNull SaleOrderCreatedVO vo) {
        submitting.setValue(false);
        idem.clear();
        notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, vo.getId());
        openDetail.setValue(NavTarget.of(RouterPath.E07).put(FinanceExtras.SALE_ORDER_ID, vo.getId())
                .put(RouterPath.EXTRA_SWITCH_TIP, createdText(vo)).replace());
    }

    /** “已登记出栏单，ZM2610-019 存栏剩 90 头”/“已登记出栏单，ZM2610-019 已全部出栏” */
    @NonNull
    public static String createdText(@NonNull SaleOrderCreatedVO vo) {
        if (vo.isBatchEnded() || vo.getRemainingCount() <= 0) {
            return "已登记出栏单，" + vo.getBatchNo() + " 已全部出栏";
        }
        return "已登记出栏单，" + vo.getBatchNo() + " 存栏剩 " + FinanceFormat.count(vo.getRemainingCount(),
                vo.getCountUnitText());
    }

    private void onSubmitError(@NonNull ApiException e, @Nullable String key) {
        if (key != null && FinanceErrors.isTimeout(e)) {
            confirmSaved(key);
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(FinanceErrors.MSG_NETWORK_KEPT);
            return;
        }
        idem.clear();
        Map<String, String> errors = new LinkedHashMap<>(fieldErrors.getValue());
        if (e.getCode() == ErrorCodes.WITHDRAWAL_PERIOD) {
            inWithdrawal.setValue(true);
            String sellable = e.dataString("sellable_date");
            if (sellable != null) {
                sellableDate.setValue(sellable);
            }
            errors.put(F_BATCH, e.getMessage());
            fieldErrors.setValue(errors);
            return;
        }
        if (e.getCode() == ErrorCodes.COUNT_EXCEEDED) {
            Integer current = dataInt(e, "current_count");
            if (current != null) {
                SaleOrderDetailVO d = editDetail.getValue();
                maxQuantity.setValue(current + (d == null ? 0 : d.getQuantity()));
            }
            errors.put(F_QUANTITY, e.getMessage());
            fieldErrors.setValue(errors);
            return;
        }
        handleError(e);
    }

    /** 提交超时：API-E43 确认，已保存时按 biz_id 打开 E07 */
    void confirmSaved(@NonNull String key) {
        launch(repository.getSubmission(key), s -> {
            submitting.setValue(false);
            Long id = s.getSaleOrderId() != null ? s.getSaleOrderId() : s.getBizId();
            if (s.isSaved() && id != null) {
                idem.clear();
                openDetail.setValue(NavTarget.of(RouterPath.E07).put(FinanceExtras.SALE_ORDER_ID, id)
                        .put(RouterPath.EXTRA_SWITCH_TIP, "已登记出栏单").replace());
            } else {
                showMessage(FinanceErrors.MSG_TIMEOUT);
            }
        }, e -> {
            submitting.setValue(false);
            showMessage(FinanceErrors.MSG_TIMEOUT);
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    @Nullable
    private static Integer dataInt(@NonNull ApiException e, @NonNull String key) {
        JsonElement data = e.getData();
        if (data == null || !data.isJsonObject()) {
            return null;
        }
        JsonElement v = data.getAsJsonObject().get(key);
        try {
            return v == null || v.isJsonNull() ? null : v.getAsInt();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ===== 返回 =====

    public boolean onBack() {
        if (!isDirty()) {
            return false;
        }
        confirmDiscard.setValue(true);
        return true;
    }

    boolean isDirty() {
        SaleOrderDetailVO d = editDetail.getValue();
        if (d == null) {
            return !isBlank(quantityText.getValue()) || !isBlank(totalWeightText.getValue())
                    || !isBlank(unitPriceText.getValue()) || !isBlank(dueDate.getValue());
        }
        return !Objects.equals(quantityText.getValue(), String.valueOf(d.getQuantity()))
                || !Objects.equals(totalWeightText.getValue(), plain(d.getTotalWeight()))
                || !Objects.equals(unitPriceText.getValue(), plain(d.getUnitPrice()))
                || !Objects.equals(buyerName.getValue(), d.getBuyerName())
                || !Objects.equals(saleDate.getValue(), d.getSaleDate())
                || !Objects.equals(emptyToNull(dueDate.getValue()), d.getDueDate())
                || !Objects.equals(pricingMethod.getValue(), d.getPricingMethod());
    }

    @NonNull
    private static String plain(@Nullable BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return isBlank(s) ? null : s;
    }
}
