package com.zhimu.slmp.today.model.vo;

/**
 * 全部已读结果（API-T14；客户端暂定类名，见技术文档待定事项 1）。
 */
public class ReadAllResult {

    private int updatedCount;
    private int totalUnread;

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getTotalUnread() {
        return totalUnread;
    }
}
