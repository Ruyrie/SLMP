package com.zhimu.slmp.today.ui.invalid;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;

import javax.inject.Inject;

/**
 * T08 关联记录已失效（RecordInvalidActivity），技术文档 2.4.7：顶部栏标题为目标页面名，标题与说明来自 OpenResultVO 的
 * page_title、deleted_title、deleted_desc（启动参数，存 SavedStateHandle）；只带 message_id 时取 API-T16 同名字段。
 */
public class RecordInvalidViewModel extends BaseViewModel {

    private static final String TAG = "RecordInvalidVM";
    static final String DEFAULT_TITLE = "该记录已被删除，无法查看";
    static final String DEFAULT_DESC = "这条消息关联的记录已被删除，相关的待办和记录不再显示。消息本身仍保留在消息中心。";

    private final MessageRepository repository;

    private final MutableLiveData<String> pageTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> title = new MutableLiveData<>("");
    private final MutableLiveData<String> desc = new MutableLiveData<>("");
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    @Inject
    public RecordInvalidViewModel(@NonNull MessageRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init(handle.get(RouterPath.EXTRA_PAGE_TITLE), handle.get(NavTarget.RecordInvalidKeys.DELETED_TITLE),
                handle.get(NavTarget.RecordInvalidKeys.DELETED_DESC), handle.get(RouterPath.EXTRA_MESSAGE_ID));
    }

    public LiveData<String> getPageTitle() {
        return pageTitle;
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<String> getDesc() {
        return desc;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    /** 写三个字段，缺字段时按 message_id 调 API-T16 */
    public void init(@Nullable String page, @Nullable String deletedTitle, @Nullable String deletedDesc,
                     @Nullable Object messageId) {
        pageTitle.setValue(Texts.orEmpty(page));
        title.setValue(Texts.isEmpty(deletedTitle) ? DEFAULT_TITLE : deletedTitle);
        desc.setValue(Texts.isEmpty(deletedDesc) ? (Texts.isEmpty(deletedTitle) ? DEFAULT_DESC : "") : deletedDesc);
        long id = messageId instanceof Number ? ((Number) messageId).longValue() : 0;
        if ((Texts.isEmpty(page) || Texts.isEmpty(deletedTitle)) && id > 0) {
            launch(repository.getDetail(id), this::applyDetail, e -> Log.i(TAG, "详情读取失败：" + e.getMessage()));
        }
    }

    private void applyDetail(@NonNull MessageDetailVO vo) {
        if (!Texts.isEmpty(vo.getPageTitle())) {
            pageTitle.setValue(vo.getPageTitle());
        }
        if (!Texts.isEmpty(vo.getDeletedTitle())) {
            title.setValue(vo.getDeletedTitle());
        }
        if (!Texts.isEmpty(vo.getDeletedDesc())) {
            desc.setValue(vo.getDeletedDesc());
        }
    }

    /** “返回消息中心”：回到 T06 并清除中间页 */
    public void onBackClick() {
        navEvent.setValue(NavTarget.of("T06"));
    }
}
