package com.zhimu.slmp.common.app;

import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.network.RxResult;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 组件单独运行时的启动页（只在 src/alone 清单中声明为 LAUNCHER）：先走隐私政策；
 * “以所有者登录 / 以员工登录”直接调用 Mock 的 API-A04 并保存会话，再调用 API-F02 写入 FarmContext；
 * 下方列出各组件入口 debugPages() 的页面，点击用 Router 打开。
 */
public class AloneLauncherActivity extends BaseActivity {

    public static final String OWNER_PHONE = "13800006620";
    public static final String STAFF_PHONE = "13700005678";
    public static final String MOCK_PASSWORD = "Zmt12345";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private CoreComponent core;
    private AloneDebugApi api;
    private TextView status;
    private LinearLayout pages;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = CoreInjector.get(this);
        api = core.retrofit().create(AloneDebugApi.class);
        setContentView(buildContent());
        PrivacyManager.ensureAgreed(this, this::render);
    }

    private ScrollView buildContent() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text(R.string.common_core_alone_title, com.google.android.material.R.attr.textAppearanceTitleLarge);
        root.addView(title);
        status = text(R.string.common_core_alone_not_logged_in, com.google.android.material.R.attr.textAppearanceBodyMedium);
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status);

        root.addView(button(getString(R.string.common_core_alone_login_owner), v -> login(OWNER_PHONE)));
        root.addView(button(getString(R.string.common_core_alone_login_staff), v -> login(STAFF_PHONE)));
        root.addView(button(getString(R.string.common_core_alone_logout), v -> {
            core.sessionManager().logout();
            render();
        }));

        TextView pagesTitle = text(R.string.common_core_alone_pages, com.google.android.material.R.attr.textAppearanceTitleMedium);
        pagesTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(pagesTitle);
        pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        root.addView(pages);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void render() {
        AccountUserVO user = core.sessionManager().currentUser();
        FarmContextVO farm = core.farmContext().current();
        if (user == null || !core.sessionManager().isLoggedIn()) {
            status.setText(R.string.common_core_alone_not_logged_in);
        } else {
            String farmText = farm == null ? "没有养殖场" : farm.getName() + " · " + farm.getRoleText();
            status.setText(getString(R.string.common_core_alone_logged_in, user.getName(), farmText));
        }
        pages.removeAllViews();
        int count = 0;
        for (IModuleEntry entry : BaseApplication.from(this).getModuleEntries()) {
            for (DebugPage page : entry.debugPages()) {
                pages.addView(button(page.getTitle(), v -> Router.open(this, page.getAction(), page.getExtras())));
                count++;
            }
        }
        if (count == 0) {
            pages.addView(text(R.string.common_core_alone_no_pages, com.google.android.material.R.attr.textAppearanceBodyMedium));
        }
    }

    private void login(String phone) {
        JsonObject body = new JsonObject();
        body.addProperty("phone", phone);
        body.addProperty("password", MOCK_PASSWORD);
        body.addProperty("device_id", core.installIdProvider().get());
        body.addProperty("device_model", Build.MODEL);
        body.addProperty("os_version", "Android " + Build.VERSION.RELEASE);
        disposables.add(RxResult.unwrap(api.loginByPassword(body))
                .flatMap(token -> {
                    core.sessionManager().saveToken(token);
                    Long farmId = token.getLastFarmId();
                    if (farmId == null) {
                        core.farmContext().clear();
                        return Single.just(token);
                    }
                    return RxResult.unwrap(api.getFarmContext(farmId))
                            .map(ctx -> {
                                core.farmContext().update(ctx);
                                return token;
                            });
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((AuthTokenVO t) -> render(),
                        e -> showMessage(RxResult.toApiException(e).getMessage())));
    }

    private TextView text(int resId, int appearanceAttr) {
        TextView tv = new TextView(this);
        tv.setText(resId);
        TypedValue tvAttr = new TypedValue();
        if (getTheme().resolveAttribute(appearanceAttr, tvAttr, true)) {
            tv.setTextAppearance(tvAttr.resourceId);
        }
        return tv;
    }

    private MaterialButton button(CharSequence label, android.view.View.OnClickListener l) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && core.appSettings().isPrivacyAgreed()) {
            render();
        }
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
