package com.zhimu.slmp.account.ui.about;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.model.vo.AgreementItemVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.app.PrivacyDialogFragment;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 协议全文页（RouterPath.A07_AGREEMENT，二级页“返回 + 协议名”）：只承载 WebView，只允许 HTTPS，不开启 JavaScript 接口，不建 ViewModel。
 * 参数：{@link RouterPath#EXTRA_URL} + {@link RouterPath#EXTRA_TITLE}；隐私政策对话框只传 {@link RouterPath#EXTRA_MODE}
 * （USER_AGREEMENT / PRIVACY_POLICY）时先取 API-A21 的地址。
 */
public class AgreementActivity extends BaseActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private WebView webView;
    private MaterialToolbar toolbar;

    /** 由协议条目打开 */
    public static void open(@NonNull Context context, @NonNull AgreementItemVO item) {
        Bundle extras = new Bundle();
        extras.putString(RouterPath.EXTRA_URL, item.getUrl());
        extras.putString(RouterPath.EXTRA_TITLE, item.getTitle());
        Router.open(context, RouterPath.A07_AGREEMENT, extras);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_activity_agreement);
        toolbar = findViewById(com.zhimu.slmp.base.R.id.base_toolbar);
        webView = findViewById(R.id.account_agreement_web);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 只允许 HTTPS
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }
        });

        String url = getIntent().getStringExtra(RouterPath.EXTRA_URL);
        String title = getIntent().getStringExtra(RouterPath.EXTRA_TITLE);
        TopAppBarHelper.setupBack(toolbar, stripBrackets(title));
        if (!TextUtils.isEmpty(url)) {
            load(url);
            return;
        }
        String mode = getIntent().getStringExtra(RouterPath.EXTRA_MODE);
        disposables.add(AccountInjector.component().accountRepository().getAgreement()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((AgreementVO a) -> {
                    AgreementItemVO item = PrivacyDialogFragment.MODE_PRIVACY_POLICY.equals(mode)
                            ? a.getPrivacyPolicy() : a.getUserAgreement();
                    if (item != null) {
                        toolbar.setTitle(stripBrackets(item.getTitle()));
                        load(item.getUrl());
                    }
                }, e -> showMessage(com.zhimu.slmp.base.error.ApiException.from(e).getMessage())));
    }

    private void load(@Nullable String url) {
        if (url == null || !url.startsWith("https://")) {
            showMessage(getString(R.string.account_agreement_only_https));
            return;
        }
        webView.loadUrl(url);
    }

    @Nullable
    private static String stripBrackets(@Nullable String title) {
        return title == null ? null : title.replace("《", "").replace("》", "");
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
