package com.zhimu.slmp.feed.ui.register;

/** G02 页面模式：新增（按任务登记）、修改（所有者、场长当天记录）、只读（员工已确认、非当天、已作废、审核中）。 */
public enum FeedRegisterMode {
    CREATE,
    EDIT,
    READONLY
}
