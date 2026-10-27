package com.zhimu.slmp.account.ui.about;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.BuildConfig;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.model.vo.AgreementItemVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.app.PrivacyDialogFragment;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 协议全文页：HTTPS 或已配置 HTTP 服务的同源页面，不开启 JavaScript 和本地文件访问。
 * 参数：{@link RouterPath#EXTRA_URL} + {@link RouterPath#EXTRA_TITLE}；隐私政策对话框只传 {@link RouterPath#EXTRA_MODE}
 * （USER_AGREEMENT / PRIVACY_POLICY）时先取 API-A21 的地址。
 */
public class AgreementActivity extends BaseActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private WebView webView;
    private MaterialToolbar toolbar;
    private StateLayout stateLayout;
    private boolean pageFailed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadTimeout = () -> fail(getString(R.string.account_agreement_load_failed));

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
        stateLayout = findViewById(R.id.account_agreement_state);
        stateLayout.setOnRetryListener(v -> requestDocument());
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                boolean blocked = !AgreementUrlPolicy.isAllowed(request.getUrl().toString(), BuildConfig.BASE_URL);
                if (blocked && request.isForMainFrame()) {
                    fail(getString(R.string.account_agreement_invalid_url));
                }
                return blocked;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // HTTP 错误后 WebView 仍可能发出开始/结束回调；仅显式重试能清除失败状态。
                if (pageFailed) return;
                stateLayout.showLoading();
                handler.removeCallbacks(loadTimeout);
                handler.postDelayed(loadTimeout, 30_000);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                handler.removeCallbacks(loadTimeout);
                if (!pageFailed) stateLayout.showContent();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail(getString(R.string.account_agreement_load_failed));
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) fail(getString(R.string.account_agreement_load_failed));
            }
        });

        String title = getIntent().getStringExtra(RouterPath.EXTRA_TITLE);
        if (TextUtils.isEmpty(title)) {
            title = getString(PrivacyDialogFragment.MODE_PRIVACY_POLICY.equals(
                    getIntent().getStringExtra(RouterPath.EXTRA_MODE))
                    ? R.string.account_about_privacy_policy : R.string.account_about_user_agreement);
        }
        TopAppBarHelper.setupBack(toolbar, stripBrackets(title));
        requestDocument();
    }

    private void requestDocument() {
        disposables.clear();
        handler.removeCallbacks(loadTimeout);
        webView.stopLoading();
        pageFailed = false;
        stateLayout.showLoading();
        String url = getIntent().getStringExtra(RouterPath.EXTRA_URL);
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
                    } else {
                        fail(getString(R.string.account_agreement_invalid_url));
                    }
                }, e -> fail(ApiException.from(e).getMessage())));
    }

    private void load(@Nullable String url) {
        if (!AgreementUrlPolicy.isAllowed(url, BuildConfig.BASE_URL)) {
            fail(getString(R.string.account_agreement_invalid_url));
            return;
        }
        webView.loadUrl(url.trim());
    }

    private void fail(String message) {
        pageFailed = true;
        handler.removeCallbacks(loadTimeout);
        webView.stopLoading();
        stateLayout.showError(getString(R.string.account_agreement_error_title), message, null, ErrorCodes.NETWORK);
    }

    @Nullable
    private static String stripBrackets(@Nullable String title) {
        return title == null ? null : title.replace("《", "").replace("》", "");
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
