package com.zhimu.slmp.inspect.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.common.model.vo.RecordLogVO;

import java.util.List;

/**
 * 提供修改记录（API-U14）的宿主 ViewModel：I04 巡检历史、I06 诊断详情；RecordLogBottomSheet 观察它。
 */
public interface RecordLogHost {

    @NonNull
    LiveData<List<RecordLogVO>> getRecordLogs();
}
