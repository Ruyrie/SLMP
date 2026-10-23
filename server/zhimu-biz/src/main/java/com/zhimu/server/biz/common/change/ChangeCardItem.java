package com.zhimu.server.biz.common.change;

/**
 * 原记录卡的一行（U01 record_card：[{label, value}]）。
 */
public record ChangeCardItem(String label, String value) {
}
