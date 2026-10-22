package com.zhimu.slmp.base.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * 登记类表单草稿（form_draft）：离开页面或进程回收前自动保存，再次进入时询问是否恢复；提交成功后删除；保留 7 天。
 * biz_key 区分同一页面的不同对象（例如批次 ID、任务 ID），没有时为空字符串。
 */
@Entity(tableName = "form_draft", primaryKeys = {"page_code", "farm_id", "biz_key"})
public class FormDraftEntity {

    /** 页面编号，例如 G02 */
    @NonNull
    @ColumnInfo(name = "page_code")
    public String pageCode = "";

    @ColumnInfo(name = "farm_id")
    public long farmId;

    @NonNull
    @ColumnInfo(name = "biz_key")
    public String bizKey = "";

    /** 表单内容（Gson 序列化） */
    @NonNull
    @ColumnInfo(name = "json")
    public String json = "";

    /** 毫秒时间戳 */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
