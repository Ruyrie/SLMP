package com.zhimu.slmp.health.ui.common;

import androidx.lifecycle.LiveData;

import com.zhimu.slmp.common.model.vo.RecordLogVO;

import java.util.List;

/**
 * 提供修改记录（API-U14）的页面 ViewModel：RecordLogBottomSheet 观察其 logs（null 表示加载中）。
 */
public interface RecordLogHost {

    LiveData<List<RecordLogVO>> getLogs();
}
