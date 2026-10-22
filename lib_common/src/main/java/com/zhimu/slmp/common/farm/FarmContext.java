package com.zhimu.slmp.common.farm;

import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.FarmPermissionVO;
import com.zhimu.slmp.common.session.SessionStore;
import com.zhimu.slmp.network.ClientContextProvider;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 当前养殖场上下文：ID、名称、角色、员工负责栏舍、养殖场状态与权限，持久化到 zmt_session 的 farm_context_json。
 * 切换养殖场（API-F03）、启动恢复（API-F02）后调用 update；MainActivity 与一级页观察 getContext() 重建导航与刷新数据。
 * 同时为 HeaderInterceptor 提供 X-Farm-Id。
 */
@Singleton
public class FarmContext implements ClientContextProvider {

    private final SharedPreferences prefs;
    private final Gson gson;
    private final MutableLiveData<FarmContextVO> context;
    @Nullable
    private volatile FarmContextVO current;

    @Inject
    public FarmContext(@NonNull SessionStore store, @NonNull Gson gson) {
        this.prefs = store.prefs();
        this.gson = gson;
        this.current = restore();
        this.context = new MutableLiveData<>(current);
    }

    @Nullable
    private FarmContextVO restore() {
        String json = prefs.getString(SessionStore.KEY_FARM_CONTEXT, null);
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, FarmContextVO.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 当前养殖场（可为 null：还没有养殖场） */
    public LiveData<FarmContextVO> getContext() {
        return context;
    }

    @Nullable
    public FarmContextVO current() {
        return current;
    }

    @Nullable
    @Override
    public Long currentFarmId() {
        FarmContextVO c = current;
        return c == null ? null : c.getFarmId();
    }

    /** 当前养殖场 ID，没有时为 0 */
    public long farmIdOrZero() {
        Long id = currentFarmId();
        return id == null ? 0 : id;
    }

    /** OWNER / MANAGER / STAFF，没有养殖场时为 null */
    @Nullable
    public String role() {
        FarmContextVO c = current;
        return c == null ? null : c.getRole();
    }

    @Nullable
    public String status() {
        FarmContextVO c = current;
        return c == null ? null : c.getStatus();
    }

    public boolean hasFarm() {
        return current != null;
    }

    public boolean isOwner() {
        return FarmContextVO.ROLE_OWNER.equals(role());
    }

    public boolean isManager() {
        return FarmContextVO.ROLE_MANAGER.equals(role());
    }

    public boolean isStaff() {
        return FarmContextVO.ROLE_STAFF.equals(role());
    }

    /** 所有者或场长（“养殖户”） */
    public boolean isOwnerOrManager() {
        return isOwner() || isManager();
    }

    /** 当前权限，没有养殖场时返回全部为 false 的对象 */
    @NonNull
    public FarmPermissionVO permissions() {
        FarmContextVO c = current;
        FarmPermissionVO p = c == null ? null : c.getPermissions();
        return p == null ? new FarmPermissionVO() : p;
    }

    /** 写入新的上下文（任意线程） */
    public void update(@Nullable FarmContextVO vo) {
        current = vo;
        prefs.edit().putString(SessionStore.KEY_FARM_CONTEXT, vo == null ? null : gson.toJson(vo)).apply();
        publish(vo);
    }

    public void clear() {
        current = null;
        prefs.edit().remove(SessionStore.KEY_FARM_CONTEXT).commit();
        publish(null);
    }

    private void publish(@Nullable FarmContextVO vo) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            context.setValue(vo);
        } else {
            context.postValue(vo);
        }
    }
}
