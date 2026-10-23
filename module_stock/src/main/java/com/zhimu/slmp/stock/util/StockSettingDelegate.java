package com.zhimu.slmp.stock.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.StockSettingRequest;
import com.zhimu.slmp.stock.model.vo.StockSettingVO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * “临期天数设置”面板的读写（M05、M09 共用，技术准备文档 2.4.7 / 2.4.11）：三个输入框默认 30，1–365 整数；
 * 保存带 setting.version（首次 0）。
 */
public final class StockSettingDelegate {

    public static final String F_VET = "near_days_vet";
    public static final String F_VACCINE = "near_days_vaccine";
    public static final String F_DISINFECTANT = "near_days_disinfectant";
    public static final String MSG_RANGE = "请填写 1–365 之间的整数";
    public static final String MSG_SAVED = "已保存临期天数设置";

    private final MutableLiveData<StockSettingVO> setting = new MutableLiveData<>();
    private final MutableLiveData<String> nearVet = new MutableLiveData<>("30");
    private final MutableLiveData<String> nearVaccine = new MutableLiveData<>("30");
    private final MutableLiveData<String> nearDisinfectant = new MutableLiveData<>("30");
    private final MutableLiveData<Map<String, String>> errors = new MutableLiveData<>(new LinkedHashMap<>());

    public LiveData<StockSettingVO> getSetting() {
        return setting;
    }

    public MutableLiveData<String> getNearVet() {
        return nearVet;
    }

    public MutableLiveData<String> getNearVaccine() {
        return nearVaccine;
    }

    public MutableLiveData<String> getNearDisinfectant() {
        return nearDisinfectant;
    }

    public LiveData<Map<String, String>> getErrors() {
        return errors;
    }

    /** API-M40 读到后回填三个输入框 */
    public void apply(@Nullable StockSettingVO vo) {
        setting.setValue(vo);
        nearVet.setValue(String.valueOf(vo == null ? 30 : vo.nearDaysVet));
        nearVaccine.setValue(String.valueOf(vo == null ? 30 : vo.nearDaysVaccine));
        nearDisinfectant.setValue(String.valueOf(vo == null ? 30 : vo.nearDaysDisinfectant));
        errors.setValue(new LinkedHashMap<>());
    }

    /** 保存成功后把新值写回 setting（版本 + 1） */
    public void onSaved(@NonNull StockSettingRequest req) {
        StockSettingVO vo = new StockSettingVO();
        vo.nearDaysVet = req.nearDaysVet;
        vo.nearDaysVaccine = req.nearDaysVaccine;
        vo.nearDaysDisinfectant = req.nearDaysDisinfectant;
        vo.version = req.version == null ? 1 : req.version + 1;
        setting.setValue(vo);
    }

    /** 校验并生成请求；不合格时写 errors 并返回 null */
    @Nullable
    public StockSettingRequest buildRequest() {
        Map<String, String> e = new LinkedHashMap<>();
        Integer vet = days(nearVet.getValue());
        Integer vaccine = days(nearVaccine.getValue());
        Integer dis = days(nearDisinfectant.getValue());
        if (vet == null) {
            e.put(F_VET, MSG_RANGE);
        }
        if (vaccine == null) {
            e.put(F_VACCINE, MSG_RANGE);
        }
        if (dis == null) {
            e.put(F_DISINFECTANT, MSG_RANGE);
        }
        errors.setValue(e);
        if (!e.isEmpty()) {
            return null;
        }
        StockSettingRequest req = new StockSettingRequest();
        req.nearDaysVet = vet;
        req.nearDaysVaccine = vaccine;
        req.nearDaysDisinfectant = dis;
        StockSettingVO vo = setting.getValue();
        req.version = vo == null ? 0 : vo.version;
        return req;
    }

    /** 当前设置中某类别的临期天数（没有读到时 30） */
    public int nearDaysOf(@Nullable String category) {
        StockSettingVO vo = setting.getValue();
        if (vo == null) {
            return 30;
        }
        if (StockEnums.CAT_VACCINE.equals(category)) {
            return vo.nearDaysVaccine;
        }
        if (StockEnums.CAT_DISINFECTANT.equals(category)) {
            return vo.nearDaysDisinfectant;
        }
        return vo.nearDaysVet;
    }

    @Nullable
    static Integer days(@Nullable String text) {
        BigDecimal v = StockCalc.parse(text);
        if (v == null || !StockCalc.isInteger(v) || v.compareTo(BigDecimal.ONE) < 0
                || v.compareTo(BigDecimal.valueOf(365)) > 0) {
            return null;
        }
        return v.intValue();
    }
}
