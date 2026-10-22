package com.zhimu.slmp.account.ui.about;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.vo.AgreementItemVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;

import javax.inject.Inject;

/**
 * 关于页（2.4.10 节）：版本号、用户协议与隐私政策两行（API-A21）。
 */
public class AboutViewModel extends BaseViewModel {

    public static final String TYPE_USER = "USER";
    public static final String TYPE_PRIVACY = "PRIVACY";

    private final AccountRepository repository;
    private final MutableLiveData<AgreementVO> agreement = new MutableLiveData<>(null);
    private final MutableLiveData<String> versionName = new MutableLiveData<>("");
    private final SingleLiveEvent<AgreementItemVO> openAgreement = new SingleLiveEvent<>();

    @Inject
    public AboutViewModel(@NonNull AccountRepository repository) {
        this.repository = repository;
        load();
    }

    public LiveData<AgreementVO> getAgreement() {
        return agreement;
    }

    public LiveData<String> getVersionName() {
        return versionName;
    }

    public SingleLiveEvent<AgreementItemVO> getOpenAgreement() {
        return openAgreement;
    }

    /** View 读取 PackageInfo 后传入 */
    public void setVersionName(@Nullable String version) {
        versionName.setValue(version == null ? "" : "版本 " + version);
    }

    public void load() {
        launch(repository.getAgreement(), agreement::setValue);
    }

    public void openAgreement(@NonNull String type) {
        AgreementVO a = agreement.getValue();
        AgreementItemVO item = a == null ? null : (TYPE_PRIVACY.equals(type) ? a.getPrivacyPolicy() : a.getUserAgreement());
        if (item == null) {
            load();
            return;
        }
        openAgreement.setValue(item);
    }
}
